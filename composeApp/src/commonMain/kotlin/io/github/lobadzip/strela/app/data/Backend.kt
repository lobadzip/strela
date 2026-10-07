package io.github.lobadzip.strela.app.data

import io.github.lobadzip.strela.api.DeliverRequest
import io.github.lobadzip.strela.api.LocationUpdate
import io.github.lobadzip.strela.api.LoginResponse
import io.github.lobadzip.strela.model.Courier
import io.github.lobadzip.strela.model.CourierSnapshot
import io.github.lobadzip.strela.model.Order
import io.github.lobadzip.strela.model.TrackingView
import kotlinx.coroutines.flow.Flow

/**
 * Where the delivery rules live: a Ktor server ([StrelaApi]) or this very process ([LocalBackend]).
 * Screens cannot tell the difference, which is the point: the same app is a client of a real backend
 * and a self-contained demo that works on a phone with no network.
 */
interface Backend {
    /** The whole city runs inside the app. */
    val isLocal: Boolean

    /** What the sign-in screen says about where we are connected. */
    val label: String

    var token: String?

    suspend fun login(phone: String, code: String): LoginResponse
    suspend fun demoCouriers(): List<Courier>
    suspend fun liveCodes(): List<String>
    suspend fun snapshot(): CourierSnapshot
    suspend fun setOnline(online: Boolean): CourierSnapshot
    suspend fun accept(orderId: String): Order
    suspend fun pickUp(orderId: String): Order
    suspend fun deliver(orderId: String, request: DeliverRequest): Order
    suspend fun fail(orderId: String, reason: String): Order
    suspend fun demoOrder(): Order
    suspend fun reportLocation(update: LocationUpdate)
    suspend fun tracking(code: String): TrackingView

    /** Snapshots as the world changes; completes or throws when the connection goes. */
    fun courierLive(): Flow<CourierSnapshot>
    fun trackingLive(code: String): Flow<TrackingView>

    /** A proof-of-delivery photo by the URL the order carries. */
    suspend fun photo(url: String): ByteArray?
}
