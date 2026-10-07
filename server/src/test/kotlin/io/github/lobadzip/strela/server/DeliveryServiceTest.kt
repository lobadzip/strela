package io.github.lobadzip.strela.server

import io.github.lobadzip.strela.api.LocationUpdate
import io.github.lobadzip.strela.api.LoginRequest
import io.github.lobadzip.strela.model.OrderStatus
import io.github.lobadzip.strela.model.Signature
import io.github.lobadzip.strela.server.TestCity.Companion.ALEXEY
import io.github.lobadzip.strela.server.TestCity.Companion.MARINA
import io.github.lobadzip.strela.server.TestCity.Companion.deliverRequest
import io.github.lobadzip.strela.server.api.ApiException
import io.github.lobadzip.strela.server.dispatch.DeliveryService.Intent
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeliveryServiceTest {

    @Test
    fun `demo couriers sign in with the demo code only`() = runTest {
        val city = TestCity().apply { seed() }

        val session = city.service.login(ALEXEY)
        assertEquals("c-alexey", session.courier.id)
        assertEquals("c-marina", city.service.login(MARINA).courier.id, "8-prefixed numbers work too")

        assertCode("wrong_code") { city.service.login(ALEXEY.copy(code = "1234")) }
        assertCode("unknown_phone") { city.service.login(LoginRequest("+7 999 123-45-67", "0000")) }
        val bot = assertFailsWith<ApiException> { city.service.login(LoginRequest("+7 000 000-00-11", "0000")) }
        assertEquals(HttpStatusCode.Forbidden, bot.status)
    }

    @Test
    fun `a full delivery pays the courier and records the proof`() = runTest {
        val city = TestCity().apply { seed() }
        val me = city.service.login(ALEXEY).courier.id
        city.service.setOnline(me, true)

        val order = city.service.snapshot(me).available.first()
        val accepted = city.service.accept(me, order.id)
        assertEquals(OrderStatus.ACCEPTED, accepted.status)
        assertTrue(accepted.approachRoute.isNotEmpty())

        city.driveToTarget(me)
        val pickedUp = city.service.pickUp(me, order.id)
        assertEquals(OrderStatus.PICKED_UP, pickedUp.status)

        city.driveToTarget(me)
        val delivered = city.service.deliver(me, order.id, deliverRequest(pickedUp.cashToCollectKopecks))
        assertEquals(OrderStatus.DELIVERED, delivered.status)
        assertNotNull(delivered.proof?.photoUrl)

        val after = city.service.snapshot(me)
        assertEquals(order.feeKopecks, after.stats.earnedKopecks)
        assertEquals(1, after.stats.delivered)
        assertTrue(after.stats.distanceMeters > 0)
        assertTrue(after.active.isEmpty())
        assertEquals(order.id, after.history.single().id)
        assertEquals(order.feeKopecks, after.week.last().kopecks, "today's bar includes the delivery")
    }

    @Test
    fun `the courier has to be at the door to pick up or deliver`() = runTest {
        val city = TestCity().apply { seed() }
        val me = city.service.login(ALEXEY).courier.id
        city.service.setOnline(me, true)
        val order = city.service.snapshot(me).available.maxBy { it.distanceMeters }

        city.service.accept(me, order.id)
        val far = city.service.snapshot(me).navigation!!
        if (far.remainingMeters > 150) {
            assertCode("too_far") { city.service.pickUp(me, order.id) }
        }
        city.driveToTarget(me)
        city.service.pickUp(me, order.id)
        assertCode("too_far") { city.service.deliver(me, order.id, deliverRequest(order.cashToCollectKopecks)) }
    }

    @Test
    fun `only one courier gets an order`() = runTest {
        val city = TestCity().apply { seed() }
        val alexey = city.service.login(ALEXEY).courier.id
        val marina = city.service.login(MARINA).courier.id
        city.service.setOnline(alexey, true)
        city.service.setOnline(marina, true)
        val order = city.service.snapshot(alexey).available.first()

        city.service.accept(alexey, order.id)
        assertCode("taken") { city.service.accept(marina, order.id) }
        assertCode("busy") {
            city.service.accept(alexey, city.service.snapshot(alexey).available.first().id)
        }
    }

    @Test
    fun `offline couriers cannot take orders and cannot leave with one in hand`() = runTest {
        val city = TestCity().apply { seed() }
        val me = city.service.login(ALEXEY).courier.id
        val order = city.service.snapshot(me).available.first()

        assertCode("offline") { city.service.accept(me, order.id) }
        city.service.setOnline(me, true)
        city.service.accept(me, order.id)
        assertCode("has_active_order") { city.service.setOnline(me, false) }
    }

    @Test
    fun `delivery needs a photo, a signature and the exact cash`() = runTest {
        val city = TestCity().apply { seed() }
        val me = city.service.login(ALEXEY).courier.id
        city.service.setOnline(me, true)
        val order = city.service.snapshot(me).available.first()
        city.service.accept(me, order.id)
        city.driveToTarget(me)
        city.service.pickUp(me, order.id)
        city.driveToTarget(me)

        val good = deliverRequest(order.cashToCollectKopecks)
        assertCode("photo_required") { city.service.deliver(me, order.id, good.copy(photoBase64 = null)) }
        assertCode("signature_required") {
            city.service.deliver(me, order.id, good.copy(signature = Signature(listOf(listOf(0.1f, 0.1f)))))
        }
        assertCode("cash_mismatch") {
            city.service.deliver(me, order.id, good.copy(cashCollectedKopecks = order.cashToCollectKopecks + 100))
        }
        assertCode("bad_photo") { city.service.deliver(me, order.id, good.copy(photoBase64 = "not base64!")) }

        assertEquals(OrderStatus.DELIVERED, city.service.deliver(me, order.id, good).status)
    }

    @Test
    fun `couriers browsing the pool do not see who the customer is`() = runTest {
        val city = TestCity().apply { seed() }
        val me = city.service.login(ALEXEY).courier.id
        city.service.setOnline(me, true)

        val pool = city.service.snapshot(me).available
        assertTrue(pool.isNotEmpty())
        assertTrue(pool.all { it.customerPhone.isEmpty() && it.customerName.isEmpty() && it.dropoff.note == null })

        val accepted = city.service.accept(me, pool.first().id)
        val mine = city.service.snapshot(me).active.single()
        assertEquals(accepted.customerPhone, mine.customerPhone)
        assertTrue(mine.customerPhone.isNotEmpty())
    }

    @Test
    fun `tracking shows the courier closing in and hides private details`() = runTest {
        val city = TestCity().apply { seed() }
        val me = city.service.login(ALEXEY).courier.id
        city.service.setOnline(me, true)
        val order = city.service.snapshot(me).available.first()

        val before = city.service.tracking(order.code.lowercase())
        assertNull(before.courier)
        assertNull(before.etaSeconds)

        city.service.accept(me, order.id)
        val start = city.service.tracking(order.code)
        assertEquals("Алексей", start.courier?.firstName)
        assertNull(start.dropoff.note)

        city.now += 10_000
        city.service.advance(10_000)
        val later = city.service.tracking(order.code)
        assertTrue(later.etaSeconds!! < start.etaSeconds!!, "${later.etaSeconds} < ${start.etaSeconds}")

        assertCode("not_found") { city.service.tracking("ST-0000") }
    }

    @Test
    fun `simulated couriers deliver orders on their own`() = runTest {
        val city = TestCity().apply { seed() }

        city.simulate(seconds = 15 * 60)

        val counts = city.service.orderCounts()
        assertTrue((counts[OrderStatus.DELIVERED] ?: 0) > 0, "bots delivered something: $counts")
        assertTrue((counts[OrderStatus.AVAILABLE] ?: 0) >= 2, "the pool is kept for people: $counts")
    }

    @Test
    fun `phone gps takes over from the simulator and back`() = runTest {
        val city = TestCity().apply { seed() }
        val me = city.service.login(ALEXEY).courier.id
        city.service.setOnline(me, true)
        val order = city.service.snapshot(me).available.first()
        city.service.accept(me, order.id)

        city.service.reportLocation(me, LocationUpdate(order.pickup.point, null, simulated = false))
        assertTrue(city.service.snapshot(me).navigation!!.arrived, "a real GPS fix at the shop counts as arrival")
        city.service.pickUp(me, order.id)

        city.service.reportLocation(me, LocationUpdate(order.pickup.point, null, simulated = true))
        val plan = city.service.plan()
        assertTrue(plan.any { it is Intent.Reroute && it.courierId == me })
    }

    private suspend fun assertCode(code: String, block: suspend () -> Unit) {
        val e = assertFailsWith<ApiException> { block() }
        assertEquals(code, e.code, e.message)
    }
}
