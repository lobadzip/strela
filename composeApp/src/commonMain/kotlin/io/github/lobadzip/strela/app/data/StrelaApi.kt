package io.github.lobadzip.strela.app.data

import io.github.lobadzip.strela.api.ApiError
import io.github.lobadzip.strela.api.ApiPaths
import io.github.lobadzip.strela.api.DeliverRequest
import io.github.lobadzip.strela.api.FailRequest
import io.github.lobadzip.strela.api.LocationUpdate
import io.github.lobadzip.strela.api.LoginRequest
import io.github.lobadzip.strela.api.LoginResponse
import io.github.lobadzip.strela.api.ShiftRequest
import io.github.lobadzip.strela.model.Courier
import io.github.lobadzip.strela.model.CourierSnapshot
import io.github.lobadzip.strela.model.Order
import io.github.lobadzip.strela.model.TrackingView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** A failure worth showing to the courier: the server's own words, or a plain "no connection". */
class ApiFailure(val code: String, override val message: String, val status: Int = 0) : Exception(message)

val ClientJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Shared client setup; each platform adds its engine specifics (cache, User-Agent) on top. */
fun HttpClientConfig<*>.strelaDefaults() {
    install(ContentNegotiation) { json(ClientJson) }
    install(WebSockets)
    install(HttpTimeout) {
        connectTimeoutMillis = 6_000
        requestTimeoutMillis = 15_000
    }
}

/** The app as a client of a Strela server: REST for actions, WebSockets for live state. */
class StrelaApi(private val client: HttpClient, private val baseUrl: String) : Backend {
    override val isLocal = false
    override val label: String = baseUrl.removePrefix("http://").removePrefix("https://")
    override var token: String? = null

    private fun absolute(path: String) = baseUrl.trimEnd('/') + path

    override suspend fun login(phone: String, code: String): LoginResponse = post(ApiPaths.SESSION, LoginRequest(phone, code))
    override suspend fun demoCouriers(): List<Courier> = get(ApiPaths.DEMO_COURIERS)
    override suspend fun liveCodes(): List<String> = get("/api/demo/live")
    override suspend fun snapshot(): CourierSnapshot = get(ApiPaths.ME)
    override suspend fun setOnline(online: Boolean): CourierSnapshot = post(ApiPaths.SHIFT, ShiftRequest(online))
    override suspend fun accept(orderId: String): Order = post(ApiPaths.accept(orderId))
    override suspend fun pickUp(orderId: String): Order = post(ApiPaths.pickUp(orderId))
    override suspend fun deliver(orderId: String, request: DeliverRequest): Order = post(ApiPaths.deliver(orderId), request)
    override suspend fun fail(orderId: String, reason: String): Order = post(ApiPaths.fail(orderId), FailRequest(reason))
    override suspend fun demoOrder(): Order = post(ApiPaths.DEMO_ORDER)
    override suspend fun tracking(code: String): TrackingView = get(ApiPaths.track(code))

    override suspend fun reportLocation(update: LocationUpdate) {
        call<Unit?>(nullable = true) { client.post(absolute(ApiPaths.LOCATION)) { json(update) } }
    }

    override suspend fun photo(url: String): ByteArray? =
        runCatching { client.get(absolute(url)).takeIf { it.status.isSuccess() }?.readRawBytes() }.getOrNull()

    /** Snapshots pushed by the server; completes when the socket closes, throws when it breaks. */
    override fun courierLive(): Flow<CourierSnapshot> =
        live("${ApiPaths.COURIER_SOCKET}?token=$token", CourierSnapshot.serializer())

    override fun trackingLive(code: String): Flow<TrackingView> = live(ApiPaths.trackSocket(code), TrackingView.serializer())

    private fun <T> live(path: String, serializer: KSerializer<T>): Flow<T> = channelFlow {
        val url = absolute(path).replaceFirst("http", "ws")
        client.webSocket(url) {
            for (frame in incoming) {
                if (frame is Frame.Text) send(ClientJson.decodeFromString(serializer, frame.readText()))
            }
        }
    }

    private suspend inline fun <reified T> get(path: String): T = call { client.get(absolute(path)) { auth() } }

    private suspend inline fun <reified T> post(path: String): T = call { client.post(absolute(path)) { auth() } }

    private suspend inline fun <reified T, reified B : Any> post(path: String, body: B): T =
        call { client.post(absolute(path)) { json(body) } }

    private inline fun <reified B : Any> HttpRequestBuilder.json(body: B) {
        auth()
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun HttpRequestBuilder.auth() {
        token?.let { bearerAuth(it) }
    }

    private suspend inline fun <reified T> call(nullable: Boolean = false, request: () -> HttpResponse): T {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // In the browser Ktor reports a dead network as JsError, which is a Throwable, not an Exception.
            throw ApiFailure("network", "Нет связи с сервером", 0)
        }
        if (!response.status.isSuccess()) {
            val error = runCatching { response.body<ApiError>() }.getOrNull()
            throw ApiFailure(
                code = error?.code ?: "http_${response.status.value}",
                message = error?.message ?: "Сервер ответил ошибкой ${response.status.value}",
                status = response.status.value,
            )
        }
        @Suppress("UNCHECKED_CAST")
        return if (nullable) null as T else response.body()
    }
}
