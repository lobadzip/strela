package io.github.lobadzip.strela.app.data

import io.github.lobadzip.strela.api.DeliverRequest
import io.github.lobadzip.strela.api.LocationUpdate
import io.github.lobadzip.strela.api.LoginRequest
import io.github.lobadzip.strela.api.LoginResponse
import io.github.lobadzip.strela.core.BakedRoutes
import io.github.lobadzip.strela.core.DeliveryException
import io.github.lobadzip.strela.core.DeliveryService
import io.github.lobadzip.strela.core.DemoCity
import io.github.lobadzip.strela.core.FailureKind
import io.github.lobadzip.strela.core.OrderFactory
import io.github.lobadzip.strela.core.Simulator
import io.github.lobadzip.strela.core.World
import io.github.lobadzip.strela.core.systemClock
import io.github.lobadzip.strela.model.Courier
import io.github.lobadzip.strela.model.CourierSnapshot
import io.github.lobadzip.strela.model.Order
import io.github.lobadzip.strela.model.TrackingView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * The demo city running inside the app: the same [DeliveryService] and [Simulator] the server uses,
 * fed by routes baked into the app's resources. No network beyond map tiles, nothing to deploy.
 */
class LocalBackend(
    scope: CoroutineScope,
    loadRoutes: suspend () -> ByteArray,
) : Backend {
    override val isLocal = true
    override val label = "демо на устройстве"
    override var token: String? = null

    private class City(val service: DeliveryService, val orders: OrderFactory)

    private val city = CompletableDeferred<City>()

    init {
        scope.launch(Dispatchers.Default) {
            val baked = runCatching {
                Json.decodeFromString<Map<String, String>>(loadRoutes().decodeToString())
            }.getOrDefault(emptyMap())
            val routes = BakedRoutes(baked)
            val random = Random.Default
            val service = DeliveryService(World(DemoCity.city), routes, SPEEDUP, systemClock)
            val orders = OrderFactory(random, routes, systemClock)
            Simulator(service, orders, routes, random, systemClock).start(this) {
                city.complete(City(service, orders))
            }
        }
    }

    private suspend fun <T> rules(block: suspend DeliveryService.() -> T): T = try {
        city.await().service.block()
    } catch (e: DeliveryException) {
        throw ApiFailure(e.code, e.message, e.kind.httpStatus)
    }

    private suspend fun me(): String = rules { authenticate(token) }

    override suspend fun login(phone: String, code: String): LoginResponse = rules { login(LoginRequest(phone, code)) }
    override suspend fun demoCouriers(): List<Courier> = rules { demoCouriers() }
    override suspend fun liveCodes(): List<String> = rules { liveTrackingCodes() }
    override suspend fun snapshot(): CourierSnapshot = me().let { id -> rules { snapshot(id) } }
    override suspend fun setOnline(online: Boolean): CourierSnapshot = me().let { id -> rules { setOnline(id, online) } }
    override suspend fun accept(orderId: String): Order = me().let { id -> rules { accept(id, orderId) } }
    override suspend fun pickUp(orderId: String): Order = me().let { id -> rules { pickUp(id, orderId) } }
    override suspend fun deliver(orderId: String, request: DeliverRequest): Order = me().let { id -> rules { deliver(id, orderId, request) } }
    override suspend fun fail(orderId: String, reason: String): Order = me().let { id -> rules { fail(id, orderId, reason) } }
    override suspend fun reportLocation(update: LocationUpdate) = me().let { id -> rules { reportLocation(id, update) } }
    override suspend fun tracking(code: String): TrackingView = rules { tracking(code) }

    override suspend fun demoOrder(): Order {
        val id = me()
        val city = city.await()
        val order = city.orders.create(near = rules { positionOf(id) })
        rules { publish(order) }
        return order
    }

    override suspend fun photo(url: String): ByteArray? =
        runCatching { rules { photo(url.substringAfterLast('/')) } }.getOrNull()

    override fun courierLive(): Flow<CourierSnapshot> = flow {
        val id = me()
        val service = city.await().service
        service.socketOpened(id)
        try {
            var last: CourierSnapshot? = null
            service.changes.collect {
                val snapshot = service.snapshot(id)
                // Same rule as the server: only push what actually changed.
                if (snapshot.copy(serverTime = 0) != last?.copy(serverTime = 0)) {
                    emit(snapshot)
                    last = snapshot
                }
                delay(PUSH_INTERVAL_MS)
            }
        } finally {
            withContext(NonCancellable) { service.socketClosed(id) }
        }
    }.flowOn(Dispatchers.Default)

    override fun trackingLive(code: String): Flow<TrackingView> = flow {
        val service = city.await().service
        var last: TrackingView? = null
        service.changes.collect {
            val view = rules { tracking(code) }
            if (view.copy(serverTime = 0) != last?.copy(serverTime = 0)) {
                emit(view)
                last = view
            }
            delay(PUSH_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.Default)

    private companion object {
        const val SPEEDUP = 10.0
        const val PUSH_INTERVAL_MS = 500L
    }
}

private val FailureKind.httpStatus: Int
    get() = when (this) {
        FailureKind.BAD_REQUEST -> 400
        FailureKind.UNAUTHORIZED -> 401
        FailureKind.FORBIDDEN -> 403
        FailureKind.NOT_FOUND -> 404
        FailureKind.CONFLICT -> 409
    }
