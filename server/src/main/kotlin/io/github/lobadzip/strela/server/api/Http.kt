package io.github.lobadzip.strela.server.api

import io.github.lobadzip.strela.api.ApiError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.excludeContentType
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

val ApiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Application.configureHttp() {
    install(DefaultHeaders) { header("X-Content-Type-Options", "nosniff") }
    install(ContentNegotiation) { json(ApiJson) }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }
    install(CallLogging) {
        level = Level.INFO
        filter { it.request.path().startsWith("/api") && !it.request.path().startsWith("/api/photos") }
    }
    install(Compression) {
        gzip {
            minimumSize(1024)
            excludeContentType(ContentType.Image.Any)
        }
    }
    // The web app is normally served from this origin; CORS is for the webpack dev server.
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Post)
    }
    install(StatusPages) {
        exception<ApiException> { call, e ->
            call.respond(e.status, ApiError(e.code, e.message))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", "Некорректный запрос"))
        }
        exception<Throwable> { call, e ->
            call.application.log.error("Unhandled error on {}", call.request.path(), e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal", "Что-то сломалось на сервере"))
        }
    }
}
