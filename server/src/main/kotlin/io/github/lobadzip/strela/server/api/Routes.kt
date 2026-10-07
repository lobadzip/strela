package io.github.lobadzip.strela.server.api

import io.github.lobadzip.strela.api.ApiPaths
import io.github.lobadzip.strela.api.DeliverRequest
import io.github.lobadzip.strela.api.FailRequest
import io.github.lobadzip.strela.api.LocationUpdate
import io.github.lobadzip.strela.api.LoginRequest
import io.github.lobadzip.strela.api.ShiftRequest
import io.github.lobadzip.strela.model.CourierSnapshot
import io.github.lobadzip.strela.model.TrackingView
import io.github.lobadzip.strela.server.AppComponent
import io.github.lobadzip.strela.core.DeliveryService
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer

fun Application.configureApi(component: AppComponent) {
    val service = component.service

    routing {
        get("/health") { call.respondText("ok") }

        // --- Demo helpers: who can sign in, which deliveries are live right now ---
        get(ApiPaths.DEMO_COURIERS) { call.respond(service.demoCouriers()) }
        get("/api/demo/live") { call.respond(service.liveTrackingCodes()) }
        get("/api/demo/stats") { call.respond(service.orderCounts()) }
        post(ApiPaths.DEMO_ORDER) {
            val courierId = call.courierId(service)
            val order = component.orders.create(near = service.positionOf(courierId))
            service.publish(order)
            call.respond(HttpStatusCode.Created, order)
        }

        // --- Courier app ---
        post(ApiPaths.SESSION) { call.respond(service.login(call.receive<LoginRequest>())) }
        get(ApiPaths.ME) { call.respond(service.snapshot(call.courierId(service))) }
        post(ApiPaths.SHIFT) {
            val courierId = call.courierId(service)
            call.respond(service.setOnline(courierId, call.receive<ShiftRequest>().online))
        }
        post(ApiPaths.LOCATION) {
            val courierId = call.courierId(service)
            service.reportLocation(courierId, call.receive<LocationUpdate>())
            call.respond(HttpStatusCode.NoContent)
        }
        post("${ApiPaths.ORDERS}/{id}/accept") {
            call.respond(service.accept(call.courierId(service), call.orderId()))
        }
        post("${ApiPaths.ORDERS}/{id}/pickup") {
            call.respond(service.pickUp(call.courierId(service), call.orderId()))
        }
        post("${ApiPaths.ORDERS}/{id}/deliver") {
            val courierId = call.courierId(service)
            call.respond(service.deliver(courierId, call.orderId(), call.receive<DeliverRequest>()))
        }
        post("${ApiPaths.ORDERS}/{id}/fail") {
            val courierId = call.courierId(service)
            call.respond(service.fail(courierId, call.orderId(), call.receive<FailRequest>().reason))
        }
        // Browsers cannot put headers on a WebSocket handshake, so the token rides in the query.
        webSocket(ApiPaths.COURIER_SOCKET) {
            val courierId = service.authenticate(call.request.queryParameters["token"])
            service.socketOpened(courierId)
            try {
                streamChanges(service, CourierSnapshot.serializer()) {
                    service.snapshot(courierId).let { it to it.copy(serverTime = 0) }
                }
            } finally {
                withContext(NonCancellable) { service.socketClosed(courierId) }
            }
        }

        // --- Customer tracking ---
        get("${ApiPaths.TRACK}/{code}") { call.respond(service.tracking(call.parameters["code"]!!)) }
        webSocket("${ApiPaths.TRACK}/{code}/live") {
            val code = call.parameters["code"]!!
            service.tracking(code) // fail fast on an unknown code
            streamChanges(service, TrackingView.serializer()) {
                service.tracking(code).let { it to it.copy(serverTime = 0) }
            }
        }
        get("${ApiPaths.PHOTOS}/{id}") {
            call.response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = 86_400))
            call.respondBytes(service.photo(call.parameters["id"]!!), ContentType.Image.JPEG)
        }

        // --- The Compose for Web build: courier app and tracking page on the same origin ---
        component.config.webDir?.let { dir ->
            staticFiles("/", dir) {
                // Unknown paths like /track/ST-1234 get the app, which routes itself.
                default("index.html")
                contentType { if (it.extension == "wasm") ContentType("application", "wasm") else null }
                cacheControl { if (it.name == "index.html") listOf(CacheControl.NoCache(null)) else emptyList() }
            }
        }
    }
}

private suspend fun ApplicationCall.courierId(service: DeliveryService): String =
    service.authenticate(request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim())

private fun ApplicationCall.orderId(): String = parameters["id"]!!

/**
 * Pushes a fresh state whenever the world changes, at most once per [THROTTLE_MS], and only when
 * it differs from the last one sent. [produce] returns the value and a copy with volatile fields
 * (the server clock) blanked out for the comparison.
 */
private suspend fun <T> DefaultWebSocketServerSession.streamChanges(
    service: DeliveryService,
    serializer: KSerializer<T>,
    produce: suspend () -> Pair<T, T>,
) {
    val pusher = launch {
        var lastKey: T? = null
        service.changes.collect {
            val (value, key) = produce()
            if (key != lastKey) {
                send(Frame.Text(ApiJson.encodeToString(serializer, value)))
                lastKey = key
            }
            delay(THROTTLE_MS)
        }
    }
    try {
        for (frame in incoming) {
            // Nothing to read from clients yet; draining keeps pings answered and notices the close.
        }
    } finally {
        pusher.cancel()
    }
}

private const val THROTTLE_MS = 700L
