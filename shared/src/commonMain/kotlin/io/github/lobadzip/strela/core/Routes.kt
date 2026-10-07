package io.github.lobadzip.strela.core

import io.github.lobadzip.strela.model.Geo
import io.github.lobadzip.strela.model.GeoPoint
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.roundToLong

@Serializable
data class Route(val points: List<GeoPoint>, val distanceMeters: Double)

fun interface RouteProvider {
    suspend fun route(from: GeoPoint, to: GeoPoint): Route
}

/** No network, no surprises: tests and the last-resort fallback drive along the crow's path. */
class StraightLineRoutes(private val stepMeters: Double = 40.0) : RouteProvider {
    override suspend fun route(from: GeoPoint, to: GeoPoint): Route {
        val distance = Geo.distance(from, to)
        val steps = ceil(distance / stepMeters).toInt().coerceAtLeast(1)
        val points = (0..steps).map { Geo.lerp(from, to, it.toDouble() / steps) }
        return Route(points, distance)
    }
}

/**
 * Road routes computed ahead of time for every trip the demo city can produce, so the demo drives
 * real streets with no network at all — in the browser, on a phone in the metro, anywhere.
 */
class BakedRoutes(
    private val encoded: Map<String, String>,
    private val fallback: RouteProvider = StraightLineRoutes(),
) : RouteProvider {
    override suspend fun route(from: GeoPoint, to: GeoPoint): Route {
        val line = encoded[RouteKey.of(from, to)] ?: return fallback.route(from, to)
        val points = PolylineCodec.decode(line)
        return Route(points, points.zipWithNext { a, b -> Geo.distance(a, b) }.sum())
    }

    val size: Int get() = encoded.size
}

object RouteKey {
    /** ~10 m precision: two requests from the same doorway share an entry. */
    fun of(from: GeoPoint, to: GeoPoint): String =
        "${from.lat.e4()},${from.lon.e4()};${to.lat.e4()},${to.lon.e4()}"

    private fun Double.e4() = (this * 10_000).roundToLong()
}

/** Google's encoded polyline format at 1e-5 precision: a route in a few hundred bytes. */
object PolylineCodec {
    fun encode(points: List<GeoPoint>): String = buildString {
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = (p.lat * 1e5).roundToLong()
            val lon = (p.lon * 1e5).roundToLong()
            appendValue(lat - lastLat)
            appendValue(lon - lastLon)
            lastLat = lat
            lastLon = lon
        }
    }

    fun decode(encoded: String): List<GeoPoint> {
        val points = ArrayList<GeoPoint>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val (dLat, afterLat) = readValue(encoded, index)
            val (dLon, afterLon) = readValue(encoded, afterLat)
            index = afterLon
            lat += dLat
            lon += dLon
            points += GeoPoint(lat / 1e5, lon / 1e5)
        }
        return points
    }

    private fun StringBuilder.appendValue(delta: Long) {
        var v = if (delta < 0) (delta shl 1).inv() else delta shl 1
        while (v >= 0x20) {
            append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        append((v.toInt() + 63).toChar())
    }

    private fun readValue(s: String, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (true) {
            val b = s[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to i
    }
}
