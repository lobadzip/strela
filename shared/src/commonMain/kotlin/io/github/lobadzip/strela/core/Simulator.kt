package io.github.lobadzip.strela.core

import io.github.lobadzip.strela.model.DayEarnings
import io.github.lobadzip.strela.model.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Keeps the demo city alive: couriers drive, simulated colleagues take orders and deliver them,
 * new orders keep arriving. Two loops, so a slow router call never makes couriers freeze mid-street.
 */
class Simulator(
    private val service: DeliveryService,
    private val factory: OrderFactory,
    private val routes: RouteProvider,
    private val random: Random,
    private val clock: () -> Long,
    private val log: (String, Throwable?) -> Unit = { _, _ -> },
) {
    private var lastGenerated = 0L

    suspend fun seed() {
        val now = clock()
        val today = Format.epochDay(now, DemoCity.city.utcOffsetMinutes)
        val anchors = DemoCity.homes.map { it.point } + DemoCity.shops.map { it.point }
        for (profile in DemoCity.couriers) {
            val week = (6 downTo 1).map { daysAgo ->
                val orders = random.nextInt(8, 23)
                DayEarnings(today - daysAgo, orders * random.nextLong(190, 290) * 100, orders)
            }
            service.addCourier(CourierState(profile, anchors.random(random), week))
        }
        // Back-dated so simulated couriers pick a few up right away and the map is busy from the first minute.
        repeat(SEED_ORDERS) { i ->
            service.publish(factory.create(createdAt = now - BACKDATE_MS - i * 15_000L))
        }
        lastGenerated = now
    }

    /** Moves everybody. Cheap and never waits on the network. */
    suspend fun move(dtMillis: Long) = service.advance(dtMillis)

    /** Lets simulated couriers act and keeps the order pool topped up. May wait on the router. */
    suspend fun think() {
        for (intent in service.plan()) {
            runCatching { execute(intent) }.onFailure {
                // A visitor beat the bot to the order, or the order expired: both are normal.
                if (it !is DeliveryException) log("Autopilot step $intent failed", it)
            }
        }
        val now = clock()
        if (service.availableCount() < TARGET_POOL && now - lastGenerated > GENERATE_EVERY_MS) {
            lastGenerated = now
            service.publish(factory.create(near = service.randomOnlineHumanPosition(random)))
        }
    }

    private suspend fun execute(intent: DeliveryService.Intent) {
        when (intent) {
            is DeliveryService.Intent.Accept -> service.accept(intent.courierId, intent.orderId)
            is DeliveryService.Intent.PickUp -> service.pickUp(intent.courierId, intent.orderId)
            is DeliveryService.Intent.Deliver -> service.deliverByAutopilot(intent.courierId, intent.orderId)
            is DeliveryService.Intent.Reroute -> service.reroute(intent.courierId, intent.orderId, routes.route(intent.from, intent.to).points)
        }
    }

    fun start(scope: CoroutineScope, onSeeded: () -> Unit = {}) {
        scope.launch {
            seed()
            onSeeded()
            log("Demo city is live: ${DemoCity.couriers.size} couriers, speed-up ×${service.speedup}", null)
            launch {
                var last = clock()
                while (isActive) {
                    delay(MOVE_TICK_MS)
                    val now = clock()
                    move(now - last)
                    last = now
                }
            }
            while (isActive) {
                delay(THINK_TICK_MS)
                runCatching { think() }.onFailure { log("Simulator step failed", it) }
            }
        }
    }

    companion object {
        const val SEED_ORDERS = 7
        const val BACKDATE_MS = 45_000L
        const val TARGET_POOL = 5
        const val GENERATE_EVERY_MS = 12_000L
        const val MOVE_TICK_MS = 500L
        const val THINK_TICK_MS = 1_000L
    }
}
