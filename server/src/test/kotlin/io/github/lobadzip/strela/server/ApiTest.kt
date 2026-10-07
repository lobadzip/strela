package io.github.lobadzip.strela.server

import io.github.lobadzip.strela.api.ApiError
import io.github.lobadzip.strela.api.ApiPaths
import io.github.lobadzip.strela.api.LocationUpdate
import io.github.lobadzip.strela.api.LoginResponse
import io.github.lobadzip.strela.api.ShiftRequest
import io.github.lobadzip.strela.model.CourierSnapshot
import io.github.lobadzip.strela.model.Order
import io.github.lobadzip.strela.model.OrderStatus
import io.github.lobadzip.strela.model.TrackingView
import io.github.lobadzip.strela.server.TestCity.Companion.ALEXEY
import io.github.lobadzip.strela.server.TestCity.Companion.deliverRequest
import io.github.lobadzip.strela.server.api.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest {

    private fun apiTest(block: suspend ApplicationTestBuilder.(TestCity, HttpClient) -> Unit) {
        val city = TestCity()
        runBlocking { city.seed() }
        testApplication {
            application { module(city.component, runSimulation = false) }
            val client = createClient {
                install(ContentNegotiation) { json(ApiJson) }
                install(WebSockets)
            }
            block(city, client)
        }
    }

    private suspend fun HttpClient.login(): String =
        post(ApiPaths.SESSION) {
            contentType(ContentType.Application.Json)
            setBody(ALEXEY)
        }.body<LoginResponse>().token

    private suspend fun HttpClient.teleport(token: String, order: Order, toDropoff: Boolean) {
        val point = if (toDropoff) order.dropoff.point else order.pickup.point
        val response = post(ApiPaths.LOCATION) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(LocationUpdate(point, null, simulated = false))
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `requests without a session get a readable 401`() = apiTest { _, client ->
        val response = client.get(ApiPaths.ME)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("unauthorized", response.body<ApiError>().code)
    }

    @Test
    fun `a courier completes a delivery over http`() = apiTest { _, client ->
        val token = client.login()
        client.post(ApiPaths.SHIFT) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ShiftRequest(online = true))
        }
        val order = client.get(ApiPaths.ME) { bearerAuth(token) }.body<CourierSnapshot>().available.first()

        assertEquals(HttpStatusCode.OK, client.post(ApiPaths.accept(order.id)) { bearerAuth(token) }.status)
        client.teleport(token, order, toDropoff = false)
        assertEquals(HttpStatusCode.OK, client.post(ApiPaths.pickUp(order.id)) { bearerAuth(token) }.status)
        client.teleport(token, order, toDropoff = true)

        val delivered = client.post(ApiPaths.deliver(order.id)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(deliverRequest(order.cashToCollectKopecks))
        }.body<Order>()
        assertEquals(OrderStatus.DELIVERED, delivered.status)

        val photo = client.get(delivered.proof!!.photoUrl!!)
        assertEquals(HttpStatusCode.OK, photo.status)
        assertEquals(ContentType.Image.JPEG, photo.contentType())

        val tracking = client.get(ApiPaths.track(order.code)).body<TrackingView>()
        assertEquals(OrderStatus.DELIVERED, tracking.status)
        assertEquals(delivered.proof!!.photoUrl, tracking.proofPhotoUrl)
    }

    @Test
    fun `business rule violations come back as 409 with a message for the courier`() = apiTest { _, client ->
        val token = client.login()
        val order = client.get(ApiPaths.ME) { bearerAuth(token) }.body<CourierSnapshot>().available.first()

        val response = client.post(ApiPaths.accept(order.id)) { bearerAuth(token) }
        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = response.body<ApiError>()
        assertEquals("offline", error.code)
        assertTrue(error.message.isNotBlank())
    }

    @Test
    fun `the live socket sends a snapshot straight away`() = apiTest { _, client ->
        val token = client.login()
        client.webSocket("${ApiPaths.COURIER_SOCKET}?token=$token") {
            val frame = incoming.receive() as Frame.Text
            val snapshot = ApiJson.decodeFromString(CourierSnapshot.serializer(), frame.readText())
            assertEquals("c-alexey", snapshot.courier.id)
            assertTrue(snapshot.available.isNotEmpty())
        }
    }

    @Test
    fun `unknown tracking codes are a 404`() = apiTest { _, client ->
        val response = client.get(ApiPaths.track("ST-0001"))
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("not_found", response.body<ApiError>().code)
    }
}
