package io.github.lobadzip.strela.core

/** What kind of "no" a rule said. The server turns it into an HTTP status; the app shows the message. */
enum class FailureKind { BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT }

/**
 * A failure the courier can act on. [code] is stable and machine-readable; [message] is Russian
 * and goes straight to the screen, so it says what to do, not what broke.
 */
class DeliveryException(
    val kind: FailureKind,
    val code: String,
    override val message: String,
) : RuntimeException(message)

fun badRequest(code: String, message: String) = DeliveryException(FailureKind.BAD_REQUEST, code, message)
fun unauthorized(message: String = "Войдите заново") = DeliveryException(FailureKind.UNAUTHORIZED, "unauthorized", message)
fun forbidden(message: String) = DeliveryException(FailureKind.FORBIDDEN, "forbidden", message)
fun notFound(message: String) = DeliveryException(FailureKind.NOT_FOUND, "not_found", message)
fun conflict(code: String, message: String) = DeliveryException(FailureKind.CONFLICT, code, message)
