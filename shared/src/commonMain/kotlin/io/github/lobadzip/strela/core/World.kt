package io.github.lobadzip.strela.core

import io.github.lobadzip.strela.model.City
import io.github.lobadzip.strela.model.Courier
import io.github.lobadzip.strela.model.DayEarnings
import io.github.lobadzip.strela.model.GeoPoint
import io.github.lobadzip.strela.model.Order
import io.github.lobadzip.strela.model.Polyline
import io.github.lobadzip.strela.model.Vehicle

/** A courier driving along a route on behalf of the simulator. */
class Movement(val orderId: String, val path: Polyline) {
    var travelled = 0.0
    val arrived: Boolean get() = travelled >= path.length
    val remainingMeters: Double get() = (path.length - travelled).coerceAtLeast(0.0)
}

class CourierState(val profile: DemoCourier, var position: GeoPoint, val week: List<DayEarnings>) {
    val id: String get() = profile.id

    /** Simulated couriers are always working; people start their shift themselves. */
    var online = !profile.loginAllowed
    var onlineSince: Long? = null
    var heading = 0.0

    var earnedKopecks = 0L
    var delivered = 0
    var distanceMeters = 0.0

    var movement: Movement? = null

    /** The phone reports a real position; the simulator keeps its hands off. */
    var realGps = false

    var lastHumanActivity = 0L
    var openSockets = 0

    /** Autopilot pauses at doors, the way a real courier does. */
    var busyUntil = 0L

    /**
     * Someone is holding this courier's phone. When they walk away mid-delivery, the autopilot
     * finishes the job, so the next visitor never finds an order stuck forever.
     */
    fun isHumanControlled(now: Long) = openSockets > 0 || now - lastHumanActivity < HUMAN_TIMEOUT_MS

    fun toDto() = Courier(
        id = profile.id,
        name = profile.name,
        phone = profile.phone,
        vehicle = profile.vehicle,
        rating = profile.rating,
        online = online,
        position = position,
        heading = heading,
    )

    companion object {
        const val HUMAN_TIMEOUT_MS = 90_000L
    }
}

/** All mutable state of the demo. Only [DeliveryService] touches it, and only under its lock. */
class World(val city: City) {
    val couriers = LinkedHashMap<String, CourierState>()
    val orders = LinkedHashMap<String, Order>()
    val sessions = HashMap<String, String>()

    val photos = LinkedHashMap<String, ByteArray>()

    /** Keeps the newest photos only: the demo runs for days and nobody needs last week's doorways. */
    fun storePhoto(id: String, bytes: ByteArray) {
        photos[id] = bytes
        while (photos.size > MAX_PHOTOS) photos.remove(photos.keys.first())
    }

    fun activeOrderOf(courierId: String): Order? = orders.values.firstOrNull { it.courierId == courierId && it.isActive }

    companion object {
        const val MAX_PHOTOS = 200
    }
}

/** Typical city speeds in m/s, before the demo speed-up. */
val Vehicle.metersPerSecond: Double
    get() = when (this) {
        Vehicle.FOOT -> 1.5
        Vehicle.BIKE -> 4.5
        Vehicle.CAR -> 6.5
    }
