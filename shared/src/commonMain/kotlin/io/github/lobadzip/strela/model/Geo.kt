package io.github.lobadzip.strela.model

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in metres. Plenty accurate at city scale. */
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val dLat = (b.lat - a.lat).toRadians()
        val dLon = (b.lon - a.lon).toRadians()
        val h = sin(dLat / 2).let { it * it } +
            cos(a.lat.toRadians()) * cos(b.lat.toRadians()) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Compass bearing from [a] to [b], 0° = north, clockwise. */
    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = a.lat.toRadians()
        val lat2 = b.lat.toRadians()
        val dLon = (b.lon - a.lon).toRadians()
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (atan2(y, x).toDegrees() + 360) % 360
    }

    /** Linear interpolation; fine for the few metres between two route vertices. */
    fun lerp(a: GeoPoint, b: GeoPoint, t: Double): GeoPoint =
        GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)

    private fun Double.toRadians() = this * PI / 180
    private fun Double.toDegrees() = this * 180 / PI
}

/**
 * A route with precomputed cumulative distances, so "where is the courier after N metres"
 * is a binary search instead of a walk over every vertex.
 */
class Polyline(val points: List<GeoPoint>) {
    private val cumulative = DoubleArray(points.size)

    init {
        require(points.isNotEmpty()) { "A polyline needs at least one point" }
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + Geo.distance(points[i - 1], points[i])
        }
    }

    val length: Double get() = cumulative.last()

    fun pointAt(distance: Double): GeoPoint {
        val (i, t) = locate(distance)
        return if (i >= points.lastIndex) points.last() else Geo.lerp(points[i], points[i + 1], t)
    }

    fun bearingAt(distance: Double): Double {
        if (points.size < 2) return 0.0
        val i = locate(distance).first.coerceAtMost(points.lastIndex - 1)
        return Geo.bearing(points[i], points[i + 1])
    }

    /** What is left to drive after [distance] metres, starting exactly at the courier. */
    fun remainingAfter(distance: Double): List<GeoPoint> {
        if (distance <= 0) return points
        val (i, _) = locate(distance)
        if (i >= points.lastIndex) return listOf(points.last())
        return listOf(pointAt(distance)) + points.subList(i + 1, points.size)
    }

    /** Index of the segment containing [distance] and the fraction travelled along it. */
    private fun locate(distance: Double): Pair<Int, Double> {
        if (points.size == 1 || distance <= 0) return 0 to 0.0
        if (distance >= length) return points.lastIndex to 0.0
        var lo = 0
        var hi = points.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (cumulative[mid] <= distance) lo = mid else hi = mid
        }
        val segment = cumulative[lo + 1] - cumulative[lo]
        val t = if (segment == 0.0) 0.0 else (distance - cumulative[lo]) / segment
        return lo to t
    }
}
