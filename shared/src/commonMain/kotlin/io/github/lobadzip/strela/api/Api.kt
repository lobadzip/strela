package io.github.lobadzip.strela.api

import io.github.lobadzip.strela.model.Courier
import io.github.lobadzip.strela.model.GeoPoint
import io.github.lobadzip.strela.model.Signature
import kotlinx.serialization.Serializable

/** Paths shared by the server and the apps, so a typo cannot survive compilation on one side only. */
object ApiPaths {
    const val SESSION = "/api/session"
    const val DEMO_COURIERS = "/api/demo/couriers"
    const val DEMO_ORDER = "/api/demo/order"
    const val ME = "/api/courier"
    const val SHIFT = "/api/courier/shift"
    const val LOCATION = "/api/courier/location"
    const val COURIER_SOCKET = "/api/courier/live"
    const val ORDERS = "/api/orders"
    const val TRACK = "/api/track"
    const val PHOTOS = "/api/photos"

    fun accept(orderId: String) = "$ORDERS/$orderId/accept"
    fun pickUp(orderId: String) = "$ORDERS/$orderId/pickup"
    fun deliver(orderId: String) = "$ORDERS/$orderId/deliver"
    fun fail(orderId: String) = "$ORDERS/$orderId/fail"
    fun track(code: String) = "$TRACK/$code"
    fun trackSocket(code: String) = "$TRACK/$code/live"
}

@Serializable
data class LoginRequest(val phone: String, val code: String)

@Serializable
data class LoginResponse(val token: String, val courier: Courier)

@Serializable
data class ShiftRequest(val online: Boolean)

/**
 * In demo mode the server drives the courier along the route; with real GPS the phone reports
 * its position here instead.
 */
@Serializable
data class LocationUpdate(val point: GeoPoint, val heading: Double?, val simulated: Boolean)

@Serializable
data class DeliverRequest(
    /** JPEG, base64. Small enough for a JSON body: the app sends a thumbnail, not the full frame. */
    val photoBase64: String?,
    val signature: Signature?,
    val cashCollectedKopecks: Long,
)

@Serializable
data class FailRequest(val reason: String)

@Serializable
data class ApiError(val code: String, val message: String)
