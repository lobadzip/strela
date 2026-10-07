package io.github.lobadzip.strela

import io.github.lobadzip.strela.model.Geo
import io.github.lobadzip.strela.model.GeoPoint
import io.github.lobadzip.strela.model.Polyline
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoTest {
    private val kremlin = GeoPoint(55.7520, 37.6175)
    private val bolshoi = GeoPoint(55.7601, 37.6186)

    @Test
    fun `distance between two Moscow landmarks is about 900 metres`() {
        val d = Geo.distance(kremlin, bolshoi)
        assertTrue(abs(d - 903) < 10, "got $d")
    }

    @Test
    fun `bearing due north is zero and due east is ninety`() {
        val origin = GeoPoint(55.0, 37.0)
        assertTrue(abs(Geo.bearing(origin, GeoPoint(55.1, 37.0))) < 0.01)
        assertTrue(abs(Geo.bearing(origin, GeoPoint(55.0, 37.1)) - 90) < 0.1)
    }

    @Test
    fun `point at a distance walks along the segments`() {
        val line = Polyline(listOf(GeoPoint(55.0, 37.0), GeoPoint(55.001, 37.0), GeoPoint(55.001, 37.002)))
        val first = Geo.distance(line.points[0], line.points[1])

        assertEquals(line.points[0], line.pointAt(-5.0))
        assertEquals(line.points[1], line.pointAt(first))
        assertEquals(line.points[2], line.pointAt(line.length + 100))

        val halfway = line.pointAt(first / 2)
        assertTrue(abs(halfway.lat - 55.0005) < 1e-9)
    }

    @Test
    fun `remaining route starts at the courier and keeps the rest`() {
        val line = Polyline(listOf(GeoPoint(55.0, 37.0), GeoPoint(55.001, 37.0), GeoPoint(55.002, 37.0)))
        val rest = line.remainingAfter(line.length * 0.25)

        assertEquals(3, rest.size)
        assertTrue(abs(rest.first().lat - 55.0005) < 1e-6)
        assertEquals(line.points.last(), rest.last())
        assertEquals(listOf(line.points.last()), line.remainingAfter(line.length))
    }

    @Test
    fun `single point polyline is valid and has zero length`() {
        val line = Polyline(listOf(kremlin))
        assertEquals(0.0, line.length)
        assertEquals(kremlin, line.pointAt(100.0))
    }
}
