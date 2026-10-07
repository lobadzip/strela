package io.github.lobadzip.strela.app.platform

import androidx.compose.runtime.Composable
import io.github.lobadzip.strela.model.GeoPoint
import kotlinx.coroutines.flow.Flow

/** SharedPreferences on Android, localStorage in the browser. */
interface KeyValueStore {
    operator fun get(key: String): String?
    operator fun set(key: String, value: String?)
}

/** Things only the host platform can do: dial, hand off to a navigator, open a link. */
interface PlatformServices {
    val isWeb: Boolean
    fun dial(phone: String)
    fun openNavigator(point: GeoPoint, label: String)
    fun openUrl(url: String)

    /** A stand-in photo for demos on devices without a camera, and for visitors in a desktop browser. */
    suspend fun demoPhoto(): ByteArray
}

data class LocationFix(val point: GeoPoint, val heading: Double?)

/** Real GPS. Absent in the browser, where the demo always drives the courier itself. */
interface LocationSource {
    /** Emits fixes while collected. Requires the location permission to have been granted. */
    fun updates(): Flow<LocationFix>
}

/** Returns a launcher; the photo arrives as a JPEG already scaled down for upload. */
@Composable
expect fun rememberPhotoCapture(onCaptured: (ByteArray) -> Unit): () -> Unit

/** Returns a launcher that asks for location access and reports whether it was granted. */
@Composable
expect fun rememberLocationPermission(onResult: (Boolean) -> Unit): () -> Unit
