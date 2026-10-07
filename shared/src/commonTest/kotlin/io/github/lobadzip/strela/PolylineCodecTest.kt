package io.github.lobadzip.strela

import io.github.lobadzip.strela.core.BakedRoutes
import io.github.lobadzip.strela.core.PolylineCodec
import io.github.lobadzip.strela.core.RouteKey
import io.github.lobadzip.strela.model.GeoPoint
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolylineCodecTest {
    // The worked example from Google's description of the format.
    private val points = listOf(GeoPoint(38.5, -120.2), GeoPoint(40.7, -120.95), GeoPoint(43.252, -126.453))
    private val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    @Test
    fun `encodes the reference example`() {
        assertEquals(encoded, PolylineCodec.encode(points))
    }

    @Test
    fun `decodes back to the same points`() {
        val decoded = PolylineCodec.decode(encoded)
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (a, b) ->
            assertTrue(abs(a.lat - b.lat) < 1e-5 && abs(a.lon - b.lon) < 1e-5, "$a vs $b")
        }
    }

    @Test
    fun `baked routes answer by key and fall back to a straight line`() = runTest {
        val from = GeoPoint(55.7569, 37.6343)
        val to = GeoPoint(55.7618, 37.6450)
        val road = listOf(from, GeoPoint(55.7590, 37.6380), to)
        val routes = BakedRoutes(mapOf(RouteKey.of(from, to) to PolylineCodec.encode(road)))

        assertEquals(3, routes.route(from, to).points.size)
        val back = routes.route(to, from)
        assertEquals(to, back.points.first())
        assertEquals(from, back.points.last())
    }
}
