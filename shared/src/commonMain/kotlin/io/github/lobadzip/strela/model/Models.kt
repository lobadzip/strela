package io.github.lobadzip.strela.model

import kotlinx.serialization.Serializable

@Serializable
enum class Vehicle { FOOT, BIKE, CAR }

/**
 * AVAILABLE → ACCEPTED → PICKED_UP → DELIVERED, with FAILED as the only way out sideways.
 * The server rejects any jump that skips a step.
 */
@Serializable
enum class OrderStatus { AVAILABLE, ACCEPTED, PICKED_UP, DELIVERED, FAILED }

@Serializable
enum class OrderTag { FRAGILE, HOT, HEAVY, DOCUMENTS }

@Serializable
data class Place(
    val title: String,
    val address: String,
    val point: GeoPoint,
    /** Entrance, floor, intercom code — whatever the courier needs at the door. */
    val note: String? = null,
)

@Serializable
data class OrderItem(val name: String, val quantity: Int)

/** Signature strokes, each a flat list of x,y pairs normalised to 0..1 of the pad size. */
@Serializable
data class Signature(val strokes: List<List<Float>>) {
    val isBlank: Boolean get() = strokes.sumOf { it.size / 2 } < 8
}

/** The reasons a courier can pick from when a delivery cannot be completed. */
val FailureReasons = listOf(
    "Клиент не выходит на связь",
    "Неверный адрес",
    "Клиент отказался от заказа",
    "Заказ повреждён",
)

@Serializable
data class DeliveryProof(
    val photoUrl: String?,
    val signature: Signature?,
    val cashCollectedKopecks: Long,
)

@Serializable
data class Order(
    val id: String,
    /** Short code the customer sees in their tracking link, e.g. ST-4821. */
    val code: String,
    val status: OrderStatus,
    val pickup: Place,
    val dropoff: Place,
    val customerName: String,
    val customerPhone: String,
    val items: List<OrderItem>,
    val tags: List<OrderTag> = emptyList(),
    val totalKopecks: Long,
    /** Zero when the order is prepaid. */
    val cashToCollectKopecks: Long,
    /** What the courier earns for the delivery. */
    val feeKopecks: Long,
    /** Road distance from pickup to drop-off. */
    val distanceMeters: Int,
    val createdAt: Long,
    val deliverBy: Long,
    val courierId: String? = null,
    val acceptedAt: Long? = null,
    val pickedUpAt: Long? = null,
    val finishedAt: Long? = null,
    /** Courier → pickup, set when the order is accepted. */
    val approachRoute: List<GeoPoint> = emptyList(),
    /** Pickup → drop-off along the roads. */
    val route: List<GeoPoint> = emptyList(),
    val proof: DeliveryProof? = null,
    val failureReason: String? = null,
) {
    val isActive: Boolean get() = status == OrderStatus.ACCEPTED || status == OrderStatus.PICKED_UP
    val isFinished: Boolean get() = status == OrderStatus.DELIVERED || status == OrderStatus.FAILED

    /** Where the courier is heading right now. */
    val target: Place get() = if (status == OrderStatus.PICKED_UP) dropoff else pickup
}

@Serializable
data class Courier(
    val id: String,
    val name: String,
    val phone: String,
    val vehicle: Vehicle,
    val rating: Double,
    val online: Boolean,
    val position: GeoPoint,
    val heading: Double,
)

@Serializable
data class ShiftStats(
    val earnedKopecks: Long,
    val delivered: Int,
    val distanceMeters: Int,
    val onlineSince: Long?,
)

@Serializable
data class DayEarnings(val epochDay: Long, val kopecks: Long, val orders: Int)

/** Turn-by-turn state for the order in hand: what is left to drive and whether we are at the door. */
@Serializable
data class Navigation(
    val orderId: String,
    val remaining: List<GeoPoint>,
    val remainingMeters: Int,
    val etaSeconds: Int,
    /** Close enough to the target for the server to accept "picked up" or "delivered". */
    val arrived: Boolean,
)

/** Everything the courier app shows, pushed in one piece whenever any of it changes. */
@Serializable
data class CourierSnapshot(
    val courier: Courier,
    val stats: ShiftStats,
    val active: List<Order>,
    val navigation: Navigation?,
    val available: List<Order>,
    val history: List<Order>,
    val week: List<DayEarnings>,
    val city: City,
    /** Demo time runs faster than real time; the app says so instead of pretending. */
    val speedup: Double,
    val serverTime: Long,
)

@Serializable
data class City(val name: String, val center: GeoPoint, val utcOffsetMinutes: Int)

@Serializable
data class TrackedCourier(
    val firstName: String,
    val vehicle: Vehicle,
    val rating: Double,
    val position: GeoPoint,
    val heading: Double,
)

/** What a customer sees on the tracking page. No phone numbers, no other orders. */
@Serializable
data class TrackingView(
    val code: String,
    val status: OrderStatus,
    val pickup: Place,
    val dropoff: Place,
    val items: List<OrderItem>,
    val totalKopecks: Long,
    val cashToCollectKopecks: Long,
    val courier: TrackedCourier?,
    /** From the courier to wherever they are going next, already trimmed to their position. */
    val remainingRoute: List<GeoPoint>,
    /** The whole pickup → drop-off route, for drawing before a courier is assigned. */
    val route: List<GeoPoint>,
    val etaSeconds: Int?,
    val createdAt: Long,
    val acceptedAt: Long?,
    val pickedUpAt: Long?,
    val finishedAt: Long?,
    val deliverBy: Long,
    val proofPhotoUrl: String?,
    val failureReason: String?,
    val city: City,
    val speedup: Double,
    val serverTime: Long,
)
