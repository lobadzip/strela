package io.github.lobadzip.strela.core

import io.github.lobadzip.strela.model.Geo
import io.github.lobadzip.strela.model.GeoPoint
import io.github.lobadzip.strela.model.Order
import io.github.lobadzip.strela.model.OrderItem
import io.github.lobadzip.strela.model.OrderStatus
import io.github.lobadzip.strela.model.OrderTag
import io.github.lobadzip.strela.model.Place
import kotlin.random.Random

/** Makes plausible orders: a real shop, a real street, a road route and a fee that follows the distance. */
class OrderFactory(
    private val random: Random,
    private val routes: RouteProvider,
    private val clock: () -> Long,
) {
    private var nextNumber = random.nextInt(1_000, 8_000)

    suspend fun create(near: GeoPoint? = null, createdAt: Long = clock()): Order {
        val shop = if (near == null) {
            DemoCity.shops.random(random)
        } else {
            DemoCity.shops.sortedBy { Geo.distance(near, it.point) }.take(3).random(random)
        }
        val home = DemoCity.homes
            .filter { Geo.distance(shop.point, it.point) in DELIVERY_RANGE_M }
            .ifEmpty { DemoCity.homes }
            .random(random)
        val route = routes.route(shop.point, home.point)

        val items = shop.menu.shuffled(random).take(random.nextInt(1, 4)).map { (name, price) ->
            Triple(name, random.nextInt(1, 3), price)
        }
        val total = items.sumOf { (_, qty, price) -> qty * price }
        val prepaid = random.nextDouble() < 0.7
        val tags = buildList {
            addAll(shop.tags)
            if (OrderTag.HEAVY !in shop.tags && items.sumOf { it.second } >= 4) add(OrderTag.HEAVY)
        }

        val customer = DemoCity.customers.random(random)
        val number = nextNumber++
        return Order(
            id = "o-$number",
            code = "ST-$number",
            status = OrderStatus.AVAILABLE,
            pickup = Place(shop.title, shop.address, shop.point, shop.note),
            dropoff = Place(
                title = customer,
                address = home.address,
                point = home.point,
                note = DemoCity.doorNotes.random(random)(random),
            ),
            customerName = customer,
            customerPhone = "+7 000 ${random.nextInt(100, 999)}-${random.nextInt(10, 99)}-${random.nextInt(10, 99)}",
            items = items.map { (name, qty, _) -> OrderItem(name, qty) },
            tags = tags,
            totalKopecks = total,
            cashToCollectKopecks = if (prepaid) 0 else total,
            feeKopecks = fee(route.distanceMeters, OrderTag.HEAVY in tags),
            distanceMeters = route.distanceMeters.toInt(),
            createdAt = createdAt,
            deliverBy = createdAt + random.nextLong(45, 76) * 60_000,
            route = route.points,
        )
    }

    companion object {
        /** Short enough to be a believable courier trip, long enough to be worth watching. */
        val DELIVERY_RANGE_M = 700.0..3_500.0

        /** 150 ₽ to show up, 30 ₽ per kilometre, 50 ₽ for carrying something heavy; rounded to 10 ₽. */
        fun fee(distanceMeters: Double, heavy: Boolean): Long {
            val raw = 15_000 + distanceMeters / 1_000 * 3_000 + if (heavy) 5_000 else 0
            return (raw / 1_000).toLong() * 1_000
        }
    }
}
