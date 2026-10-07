package io.github.lobadzip.strela.server.routing

import io.github.lobadzip.strela.core.DemoCity
import io.github.lobadzip.strela.core.OrderFactory
import io.github.lobadzip.strela.core.PolylineCodec
import io.github.lobadzip.strela.core.RouteKey
import io.github.lobadzip.strela.model.Geo
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Asks OSRM, once, for every trip the demo city can produce and writes them as encoded polylines.
 * The apps ship this file, so the offline demo drives real streets with no network at all.
 *
 * `./gradlew :server:bakeRoutes` — about ten minutes, because the public router allows one request a second.
 */
fun main(args: Array<String>) = runBlocking {
    val out = File(args.firstOrNull() ?: "composeApp/src/commonMain/composeResources/files/routes.json")
    val client = HttpClient(Java) { install(HttpTimeout) { requestTimeoutMillis = 15_000 } }
    val osrm = OsrmRoutes("https://router.project-osrm.org", client, cacheFile = File("data/routes.json"))

    val homes = DemoCity.homes.map { it.point }
    val shops = DemoCity.shops.map { it.point }
    // Deliveries: every shop to every home the order factory may pick for it.
    val deliveries = DemoCity.shops.flatMap { shop ->
        DemoCity.homes
            .filter { Geo.distance(shop.point, it.point) in OrderFactory.DELIVERY_RANGE_M }
            .map { shop.point to it.point }
    }
    // Approaches: couriers only ever stand at a door when they take an order, a home or a shop.
    val approaches = (homes + shops).flatMap { from -> shops.filter { it != from }.map { from to it } }
    val trips = (deliveries + approaches).distinct()

    val baked = sortedMapOf<String, String>()
    trips.forEachIndexed { i, (from, to) ->
        val route = osrm.route(from, to)
        baked[RouteKey.of(from, to)] = PolylineCodec.encode(route.points)
        if (i % 25 == 0) println("${i + 1}/${trips.size}")
    }
    client.close()

    out.parentFile.mkdirs()
    out.writeText(Json { prettyPrint = false }.encodeToString(baked as Map<String, String>))
    println("Wrote ${baked.size} routes, ${out.length() / 1024} KB, to $out")
}
