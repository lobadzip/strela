package io.github.lobadzip.strela.server.routing

import io.github.lobadzip.strela.model.Geo
import io.github.lobadzip.strela.model.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

@Serializable
data class Route(val points: List<GeoPoint>, val distanceMeters: Double)

fun interface RouteProvider {
    suspend fun route(from: GeoPoint, to: GeoPoint): Route
}

/** No network, no surprises: tests and offline demos drive along the crow's path. */
class StraightLineRoutes(private val stepMeters: Double = 40.0) : RouteProvider {
    override suspend fun route(from: GeoPoint, to: GeoPoint): Route {
        val distance = Geo.distance(from, to)
        val steps = ceil(distance / stepMeters).toInt().coerceAtLeast(1)
        val points = (0..steps).map { Geo.lerp(from, to, it.toDouble() / steps) }
        return Route(points, distance)
    }
}

/**
 * Road routes from an OSRM server, cached on disk because the demo city reuses the same addresses
 * and the public instance asks for at most one request a second. Any failure falls back to a
 * straight line: a slightly wrong route beats a courier app that cannot accept an order.
 */
class OsrmRoutes(
    private val baseUrl: String,
    private val client: HttpClient,
    private val cacheFile: File?,
    private val fallback: RouteProvider = StraightLineRoutes(),
) : RouteProvider {
    private val log = LoggerFactory.getLogger(OsrmRoutes::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, Route>()
    private val throttle = Mutex()
    private var lastRequestAt = 0L

    init {
        cacheFile?.takeIf { it.isFile }?.let { file ->
            runCatching { json.decodeFromString<Map<String, Route>>(file.readText()) }
                .onSuccess { cache.putAll(it); log.info("Loaded {} cached routes", it.size) }
                .onFailure { log.warn("Ignoring unreadable route cache {}: {}", file, it.message) }
        }
    }

    override suspend fun route(from: GeoPoint, to: GeoPoint): Route {
        val key = key(from, to)
        cache[key]?.let { return it }

        val fetched = runCatching { fetch(from, to) }
            .onFailure { log.warn("OSRM failed for {}, using a straight line: {}", key, it.message) }
            .getOrNull()
            ?: return fallback.route(from, to)

        cache[key] = fetched
        persist()
        return fetched
    }

    private suspend fun fetch(from: GeoPoint, to: GeoPoint): Route {
        throttle.withLock {
            val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }
        val url = "$baseUrl/route/v1/driving/${from.lon},${from.lat};${to.lon},${to.lat}" +
            "?overview=full&geometries=geojson"
        val response = client.get(url) { header("User-Agent", "Strela demo (github.com/lobadzip/strela)") }
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }

        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        val route = (body["routes"] as? JsonArray)?.firstOrNull()?.jsonObject ?: error("no route")
        val coordinates = route["geometry"]!!.jsonObject["coordinates"]!!.jsonArray.map {
            val (lon, lat) = it.jsonArray.map { n -> n.jsonPrimitive.double }
            GeoPoint(lat, lon)
        }
        // OSRM snaps both ends to the nearest road; join them back to the actual doors.
        val points = buildList {
            if (Geo.distance(from, coordinates.first()) > 3) add(from)
            addAll(coordinates)
            if (Geo.distance(to, coordinates.last()) > 3) add(to)
        }
        val distance = points.zipWithNext { a, b -> Geo.distance(a, b) }.sum()
        return Route(points.map { it.rounded() }, distance)
    }

    private fun persist() {
        val file = cacheFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(HashMap(cache)))
        }.onFailure { log.warn("Could not write route cache: {}", it.message) }
    }

    private fun GeoPoint.rounded() = GeoPoint(lat.round6(), lon.round6())
    private fun Double.round6() = Math.round(this * 1e6) / 1e6

    companion object {
        private const val MIN_INTERVAL_MS = 1_100L

        /** ~10 m precision: two requests from the same doorway share a cache entry. */
        fun key(from: GeoPoint, to: GeoPoint): String =
            "%.4f,%.4f;%.4f,%.4f".format(java.util.Locale.ROOT, from.lat, from.lon, to.lat, to.lon)
    }
}
