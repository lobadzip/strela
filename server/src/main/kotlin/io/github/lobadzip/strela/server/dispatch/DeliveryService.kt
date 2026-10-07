package io.github.lobadzip.strela.server.dispatch

import io.github.lobadzip.strela.api.DeliverRequest
import io.github.lobadzip.strela.api.LocationUpdate
import io.github.lobadzip.strela.api.LoginRequest
import io.github.lobadzip.strela.api.LoginResponse
import io.github.lobadzip.strela.model.CourierSnapshot
import io.github.lobadzip.strela.model.DayEarnings
import io.github.lobadzip.strela.model.DeliveryProof
import io.github.lobadzip.strela.model.Format
import io.github.lobadzip.strela.model.Geo
import io.github.lobadzip.strela.model.GeoPoint
import io.github.lobadzip.strela.model.Navigation
import io.github.lobadzip.strela.model.Order
import io.github.lobadzip.strela.model.OrderStatus
import io.github.lobadzip.strela.model.Polyline
import io.github.lobadzip.strela.model.ShiftStats
import io.github.lobadzip.strela.model.TrackedCourier
import io.github.lobadzip.strela.model.TrackingView
import io.github.lobadzip.strela.server.api.badRequest
import io.github.lobadzip.strela.server.api.conflict
import io.github.lobadzip.strela.server.api.forbidden
import io.github.lobadzip.strela.server.api.notFound
import io.github.lobadzip.strela.server.api.unauthorized
import io.github.lobadzip.strela.server.demo.DemoCity
import io.github.lobadzip.strela.server.routing.RouteProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Base64
import java.util.UUID
import kotlin.random.Random

/**
 * The rules of the delivery business. Every read and write goes through one lock, which is plenty
 * for a demo city and makes races (two couriers grabbing the same order) impossible to get wrong.
 *
 * Calls to the router never happen under the lock: a slow network must not freeze everybody else.
 */
class DeliveryService(
    private val world: World,
    private val routes: RouteProvider,
    val speedup: Double,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val version = MutableStateFlow(0L)

    /** Bumps on every change; live connections watch it and push fresh snapshots. */
    val changes: StateFlow<Long> = version.asStateFlow()

    private suspend fun <T> read(block: World.() -> T): T = mutex.withLock { world.block() }

    private suspend fun <T> write(block: World.() -> T): T = mutex.withLock {
        try {
            world.block()
        } finally {
            version.value++
        }
    }

    // --- Sessions ---------------------------------------------------------------------------------

    suspend fun login(request: LoginRequest): LoginResponse = write {
        val digits = request.phone.normalizedPhone()
        val courier = couriers.values.firstOrNull { it.profile.phone.normalizedPhone() == digits }
            ?: throw badRequest("unknown_phone", "Номер не найден. В демо войдите как +7 000 000-00-01")
        if (!courier.profile.loginAllowed) throw forbidden("Этим курьером управляет симулятор")
        if (request.code.trim() != DemoCity.DEMO_CODE) throw badRequest("wrong_code", "Неверный код. В демо подходит 0000")

        val token = UUID.randomUUID().toString()
        sessions[token] = courier.id
        courier.lastHumanActivity = clock()
        LoginResponse(token, courier.toDto())
    }

    /** Resolves a bearer token to a courier and marks them as present. */
    suspend fun authenticate(token: String?): String = read {
        val id = token?.let { sessions[it] } ?: throw unauthorized()
        couriers.getValue(id).lastHumanActivity = clock()
        id
    }

    suspend fun socketOpened(courierId: String) = read { couriers.getValue(courierId).openSockets++ }

    suspend fun socketClosed(courierId: String) = read {
        val courier = couriers.getValue(courierId)
        courier.openSockets = (courier.openSockets - 1).coerceAtLeast(0)
        courier.lastHumanActivity = clock()
    }

    // --- Courier actions --------------------------------------------------------------------------

    suspend fun snapshot(courierId: String): CourierSnapshot = read { snapshotOf(courier(courierId)) }

    suspend fun setOnline(courierId: String, online: Boolean): CourierSnapshot = write {
        val courier = courier(courierId)
        if (!online && activeOrderOf(courierId) != null) {
            throw conflict("has_active_order", "Сначала завершите заказ — с ним на руках смену не закрыть")
        }
        if (courier.online != online) {
            courier.online = online
            courier.onlineSince = if (online) clock() else null
        }
        snapshotOf(courier)
    }

    suspend fun accept(courierId: String, orderId: String): Order {
        val (from, to) = read {
            checkCanAccept(courier(courierId), order(orderId))
            courier(courierId).position to order(orderId).pickup.point
        }
        val approach = routes.route(from, to)
        return write {
            // Someone may have taken the order while we were asking the router.
            val courier = courier(courierId)
            val order = order(orderId)
            checkCanAccept(courier, order)
            val accepted = order.copy(
                status = OrderStatus.ACCEPTED,
                courierId = courierId,
                acceptedAt = clock(),
                approachRoute = approach.points,
            )
            orders[orderId] = accepted
            if (!courier.realGps) courier.movement = Movement(orderId, Polyline(approach.points))
            accepted
        }
    }

    suspend fun pickUp(courierId: String, orderId: String): Order = write {
        val courier = courier(courierId)
        val order = ownedOrder(courier, orderId)
        if (order.status != OrderStatus.ACCEPTED) throw conflict("wrong_status", "Заказ уже у вас")
        checkArrived(courier, order)

        val pickedUp = order.copy(status = OrderStatus.PICKED_UP, pickedUpAt = clock())
        orders[orderId] = pickedUp
        if (!courier.realGps) courier.movement = Movement(orderId, Polyline(order.route))
        pickedUp
    }

    suspend fun deliver(courierId: String, orderId: String, request: DeliverRequest): Order {
        val photo = request.photoBase64?.let(::decodePhoto)
            ?: throw badRequest("photo_required", "Сфотографируйте заказ у двери")
        val signature = request.signature
        if (signature == null || signature.isBlank) {
            throw badRequest("signature_required", "Попросите клиента расписаться")
        }
        return write {
            val courier = courier(courierId)
            val order = ownedOrder(courier, orderId)
            if (order.status != OrderStatus.PICKED_UP) throw conflict("wrong_status", "Сначала заберите заказ")
            checkArrived(courier, order)
            if (request.cashCollectedKopecks != order.cashToCollectKopecks) {
                throw badRequest(
                    "cash_mismatch",
                    if (order.cashToCollectKopecks == 0L) "Заказ оплачен онлайн — деньги брать не нужно"
                    else "Получите от клиента ровно ${Format.rub(order.cashToCollectKopecks)}",
                )
            }
            val photoId = UUID.randomUUID().toString()
            photos[photoId] = photo
            finish(courier, order, DeliveryProof("/api/photos/$photoId", signature, request.cashCollectedKopecks))
        }
    }

    /** What the autopilot does at the door: no camera, no pen, but the money is right. */
    internal suspend fun deliverByAutopilot(courierId: String, orderId: String): Order = write {
        val courier = courier(courierId)
        val order = ownedOrder(courier, orderId)
        if (order.status != OrderStatus.PICKED_UP) throw conflict("wrong_status", "Сначала заберите заказ")
        finish(courier, order, DeliveryProof(null, null, order.cashToCollectKopecks))
    }

    suspend fun fail(courierId: String, orderId: String, reason: String): Order = write {
        if (reason.isBlank()) throw badRequest("reason_required", "Укажите причину")
        val courier = courier(courierId)
        val order = ownedOrder(courier, orderId)
        if (!order.isActive) throw conflict("wrong_status", "Заказ уже закрыт")
        courier.movement = null
        order.copy(status = OrderStatus.FAILED, finishedAt = clock(), failureReason = reason.take(200))
            .also { orders[orderId] = it }
    }

    suspend fun reportLocation(courierId: String, update: LocationUpdate) = write {
        val courier = courier(courierId)
        if (update.simulated) {
            // Back to demo driving; the simulator plots a route from wherever the phone left us.
            courier.realGps = false
            return@write
        }
        courier.realGps = true
        courier.movement = null
        val step = Geo.distance(courier.position, update.point)
        if (step < MAX_GPS_JUMP_M) courier.distanceMeters += step
        courier.heading = update.heading ?: if (step > 3) Geo.bearing(courier.position, update.point) else courier.heading
        courier.position = update.point
    }

    // --- Customer side ----------------------------------------------------------------------------

    suspend fun tracking(code: String): TrackingView = read {
        val order = orders.values.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
            ?: throw notFound("Заказ $code не найден")
        val courier = order.courierId?.let { couriers[it] }
        val movement = courier?.movement?.takeIf { it.orderId == order.id && order.isActive }

        val remaining = when {
            !order.isActive || courier == null -> emptyList()
            movement != null -> movement.path.remainingAfter(movement.travelled)
            else -> listOf(courier.position, order.target.point)
        }
        TrackingView(
            code = order.code,
            status = order.status,
            pickup = order.pickup,
            dropoff = order.dropoff.copy(note = null),
            items = order.items,
            totalKopecks = order.totalKopecks,
            cashToCollectKopecks = order.cashToCollectKopecks,
            courier = courier?.let {
                TrackedCourier(it.profile.name.substringBefore(' '), it.profile.vehicle, it.profile.rating, it.position, it.heading)
            },
            remainingRoute = remaining,
            route = order.route,
            etaSeconds = if (courier != null && order.isActive) etaToDoor(courier, order) else null,
            createdAt = order.createdAt,
            acceptedAt = order.acceptedAt,
            pickedUpAt = order.pickedUpAt,
            finishedAt = order.finishedAt,
            deliverBy = order.deliverBy,
            proofPhotoUrl = order.proof?.photoUrl,
            failureReason = order.failureReason,
            city = city,
            speedup = speedup,
            serverTime = clock(),
        )
    }

    suspend fun photo(id: String): ByteArray = read { photos[id] ?: throw notFound("Фото не найдено") }

    /** Codes of orders on the move right now, so the demo page can open a live tracking link. */
    suspend fun liveTrackingCodes(): List<String> = read {
        orders.values.filter { it.isActive }.sortedBy { it.acceptedAt }.map { it.code }
    }

    suspend fun orderCounts(): Map<OrderStatus, Int> = read { orders.values.groupingBy { it.status }.eachCount() }

    suspend fun demoCouriers() = read { couriers.values.filter { it.profile.loginAllowed }.map { it.toDto() } }

    // --- Simulator hooks --------------------------------------------------------------------------

    /** Seeds and generated orders enter the world here. */
    suspend fun publish(order: Order) = write { orders[order.id] = order }

    suspend fun addCourier(courier: CourierState) = write { couriers[courier.id] = courier }

    /** New orders often appear near someone who is actually watching, so the demo feels responsive. */
    suspend fun randomOnlineHumanPosition(random: Random): GeoPoint? = read {
        val now = clock()
        couriers.values
            .filter { it.online && it.isHumanControlled(now) }
            .randomOrNull(random)
            ?.position
            ?.takeIf { random.nextDouble() < 0.6 }
    }

    suspend fun positionOf(courierId: String): GeoPoint = read { courier(courierId).position }

    suspend fun availableCount(): Int = read { orders.values.count { it.status == OrderStatus.AVAILABLE } }

    /** Moves everyone along their routes by [dtMillis] of real time and tidies up old orders. */
    suspend fun advance(dtMillis: Long) = write {
        val now = clock()
        for (courier in couriers.values) {
            val movement = courier.movement ?: continue
            val before = movement.travelled
            movement.travelled = (before + courier.profile.vehicle.metersPerSecond * speedup * dtMillis / 1000)
                .coerceAtMost(movement.path.length)
            courier.distanceMeters += movement.travelled - before
            courier.position = movement.path.pointAt(movement.travelled)
            courier.heading = movement.path.bearingAt(movement.travelled)
            if (movement.arrived) {
                courier.movement = null
                courier.busyUntil = now + DOOR_DWELL_MS
            }
        }
        // Customers give up on orders nobody takes; the generator replaces them.
        orders.values.removeAll { it.status == OrderStatus.AVAILABLE && now - it.createdAt > AVAILABLE_TTL_MS }
        val finished = orders.values.filter { it.isFinished }.sortedBy { it.finishedAt }
        if (finished.size > MAX_FINISHED) finished.take(finished.size - MAX_FINISHED).forEach { orders.remove(it.id) }
    }

    /** Decides what each courier without a human should do next. Execution happens outside the lock. */
    suspend fun plan(): List<Intent> = read {
        val now = clock()
        val available = orders.values.filter { it.status == OrderStatus.AVAILABLE }.toMutableList()
        couriers.values.mapNotNull { courier ->
            val active = activeOrderOf(courier.id)
            val arrived = active != null && isAtTarget(courier, active)
            when {
                // Demo GPS lost its route (switched back from real GPS): plot a new one. Humans included.
                active != null && !courier.realGps && courier.movement == null && !arrived ->
                    Intent.Reroute(courier.id, active.id, courier.position, active.target.point)
                courier.isHumanControlled(now) || now < courier.busyUntil -> null
                active?.status == OrderStatus.ACCEPTED && arrived -> Intent.PickUp(courier.id, active.id)
                active?.status == OrderStatus.PICKED_UP && arrived -> Intent.Deliver(courier.id, active.id)
                // Only simulated couriers take new work, and never the last couple of orders in the pool.
                active == null && courier.online && !courier.profile.loginAllowed && available.size > MIN_POOL_FOR_BOTS -> {
                    available
                        .filter { now - it.createdAt > BOT_PATIENCE_MS }
                        .minByOrNull { Geo.distance(courier.position, it.pickup.point) }
                        ?.let { order ->
                            available.remove(order)
                            Intent.Accept(courier.id, order.id)
                        }
                }
                else -> null
            }
        }
    }

    suspend fun reroute(courierId: String, orderId: String, path: List<GeoPoint>) = write {
        val courier = courier(courierId)
        if (activeOrderOf(courierId)?.id == orderId && !courier.realGps) courier.movement = Movement(orderId, Polyline(path))
    }

    sealed interface Intent {
        data class Accept(val courierId: String, val orderId: String) : Intent
        data class PickUp(val courierId: String, val orderId: String) : Intent
        data class Deliver(val courierId: String, val orderId: String) : Intent
        data class Reroute(
            val courierId: String,
            val orderId: String,
            val from: GeoPoint,
            val to: GeoPoint,
        ) : Intent
    }

    // --- Internals --------------------------------------------------------------------------------

    private fun World.courier(id: String) = couriers[id] ?: throw notFound("Курьер не найден")

    private fun World.order(id: String) = orders[id] ?: throw notFound("Заказ не найден или уже отменён")

    private fun World.ownedOrder(courier: CourierState, orderId: String): Order {
        val order = order(orderId)
        if (order.courierId != courier.id) throw forbidden("Это заказ другого курьера")
        return order
    }

    private fun World.checkCanAccept(courier: CourierState, order: Order) {
        if (!courier.online) throw conflict("offline", "Выйдите на линию, чтобы брать заказы")
        if (order.status != OrderStatus.AVAILABLE) throw conflict("taken", "Этот заказ уже забрал другой курьер")
        if (activeOrderOf(courier.id) != null) throw conflict("busy", "Сначала завершите текущий заказ")
    }

    private fun isAtTarget(courier: CourierState, order: Order) =
        Geo.distance(courier.position, order.target.point) <= ARRIVAL_RADIUS_M

    private fun checkArrived(courier: CourierState, order: Order) {
        if (!isAtTarget(courier, order)) {
            val left = Geo.distance(courier.position, order.target.point).toInt()
            throw conflict("too_far", "Вы ещё не на месте: до точки ${Format.distance(left)}")
        }
    }

    private fun World.finish(courier: CourierState, order: Order, proof: DeliveryProof): Order {
        val delivered = order.copy(status = OrderStatus.DELIVERED, finishedAt = clock(), proof = proof)
        orders[order.id] = delivered
        courier.movement = null
        courier.earnedKopecks += order.feeKopecks
        courier.delivered++
        return delivered
    }

    private fun decodePhoto(base64: String): ByteArray {
        val bytes = try {
            Base64.getDecoder().decode(base64)
        } catch (_: IllegalArgumentException) {
            throw badRequest("bad_photo", "Фото не прочиталось, снимите ещё раз")
        }
        if (bytes.size > MAX_PHOTO_BYTES) throw badRequest("photo_too_large", "Фото слишком большое")
        if (bytes.size < 100) throw badRequest("bad_photo", "Фото не прочиталось, снимите ещё раз")
        return bytes
    }

    private fun effectiveSpeed(courier: CourierState) = courier.profile.vehicle.metersPerSecond * speedup

    /** Seconds until the customer gets the order, counting the wait at the shop. */
    private fun etaToDoor(courier: CourierState, order: Order): Int {
        val movement = courier.movement?.takeIf { it.orderId == order.id }
        val toTarget = movement?.remainingMeters ?: Geo.distance(courier.position, order.target.point)
        val speed = effectiveSpeed(courier)
        val seconds = when (order.status) {
            OrderStatus.ACCEPTED -> toTarget / speed + DOOR_DWELL_MS / 1000.0 + Polyline(order.route).length / speed
            else -> toTarget / speed
        }
        return seconds.toInt()
    }

    private fun World.snapshotOf(courier: CourierState): CourierSnapshot {
        val now = clock()
        val active = activeOrderOf(courier.id)
        val today = Format.epochDay(now, city.utcOffsetMinutes)
        val navigation = active?.let { order ->
            val movement = courier.movement?.takeIf { it.orderId == order.id }
            val remaining = movement?.path?.remainingAfter(movement.travelled)
                ?: listOf(courier.position, order.target.point)
            val meters = movement?.remainingMeters ?: Geo.distance(courier.position, order.target.point)
            Navigation(
                orderId = order.id,
                remaining = remaining,
                remainingMeters = meters.toInt(),
                etaSeconds = (meters / effectiveSpeed(courier)).toInt(),
                arrived = isAtTarget(courier, order),
            )
        }
        return CourierSnapshot(
            courier = courier.toDto(),
            stats = ShiftStats(courier.earnedKopecks, courier.delivered, courier.distanceMeters.toInt(), courier.onlineSince),
            active = listOfNotNull(active),
            navigation = navigation,
            available = orders.values
                .filter { it.status == OrderStatus.AVAILABLE }
                .sortedBy { Geo.distance(courier.position, it.pickup.point) }
                .take(MAX_POOL_SHOWN)
                .map { it.forPool() },
            history = orders.values
                .filter { it.courierId == courier.id && it.isFinished }
                .sortedByDescending { it.finishedAt }
                .take(30)
                .map { it.copy(approachRoute = emptyList(), route = emptyList()) },
            week = courier.week.filter { it.epochDay < today } +
                DayEarnings(today, courier.earnedKopecks, courier.delivered),
            city = city,
            speedup = speedup,
            serverTime = now,
        )
    }

    /** Until a courier takes the order, they see the street but not the flat, the name or the phone. */
    private fun Order.forPool() = copy(
        customerName = "",
        customerPhone = "",
        dropoff = dropoff.copy(title = "", note = null),
    )

    private fun String.normalizedPhone(): String {
        val digits = filter(Char::isDigit)
        return if (digits.length == 11 && digits.startsWith("8")) "7" + digits.drop(1) else digits
    }

    companion object {
        const val ARRIVAL_RADIUS_M = 150.0
        const val DOOR_DWELL_MS = 6_000L
        const val AVAILABLE_TTL_MS = 20 * 60_000L
        const val BOT_PATIENCE_MS = 40_000L
        const val MIN_POOL_FOR_BOTS = 2
        const val MAX_POOL_SHOWN = 8
        const val MAX_FINISHED = 300
        const val MAX_PHOTO_BYTES = 2 * 1024 * 1024
        const val MAX_GPS_JUMP_M = 500.0
    }
}
