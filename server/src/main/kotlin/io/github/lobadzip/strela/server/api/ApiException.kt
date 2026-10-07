package io.github.lobadzip.strela.server.api

import io.ktor.http.HttpStatusCode

/**
 * A failure the client can act on. [code] is stable and machine-readable; [message] is Russian
 * and goes straight to the courier's screen, so it says what to do, not what broke.
 */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)

fun badRequest(code: String, message: String) = ApiException(HttpStatusCode.BadRequest, code, message)
fun unauthorized(message: String = "Войдите заново") = ApiException(HttpStatusCode.Unauthorized, "unauthorized", message)
fun forbidden(message: String) = ApiException(HttpStatusCode.Forbidden, "forbidden", message)
fun notFound(message: String) = ApiException(HttpStatusCode.NotFound, "not_found", message)
fun conflict(code: String, message: String) = ApiException(HttpStatusCode.Conflict, code, message)
