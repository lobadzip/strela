package io.github.lobadzip.strela.app

import io.github.lobadzip.strela.app.data.Backend
import io.github.lobadzip.strela.app.data.CourierSession
import io.github.lobadzip.strela.app.data.LocalBackend
import io.github.lobadzip.strela.app.data.Settings
import io.github.lobadzip.strela.app.data.StrelaApi
import io.github.lobadzip.strela.app.data.TrackingSession
import io.github.lobadzip.strela.app.platform.KeyValueStore
import io.github.lobadzip.strela.app.platform.LocationSource
import io.github.lobadzip.strela.app.platform.PlatformServices
import io.github.lobadzip.strela.app.resources.Res
import io.github.lobadzip.strela.app.ui.map.TileCache
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Everything with a lifetime longer than a screen. One per process. */
class AppGraph(
    store: KeyValueStore,
    val platform: PlatformServices,
    location: LocationSource?,
    val http: HttpClient,
    /** Offered in the "connect to a server" dialog; null where the app decides by itself (the web). */
    val suggestedServerUrl: String?,
    /** The web build's own decision: the origin when it serves the API, null for the in-browser city. */
    webServerUrl: String? = null,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings = Settings(store)

    val canChooseServer: Boolean get() = suggestedServerUrl != null

    var backend: Backend = createBackend(if (canChooseServer) settings.serverUrl else webServerUrl)
        private set

    val session = CourierSession({ backend }, settings, scope, location)
    val tiles = TileCache(http, scope)

    fun trackingSession() = TrackingSession({ backend }, scope)

    /** Switches between a real server ([url]) and the demo on the device (null). Signs the courier out. */
    fun useServer(url: String?) {
        session.signOut()
        settings.serverUrl = url
        backend = createBackend(url)
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun createBackend(url: String?): Backend =
        if (url == null) LocalBackend(scope) { Res.readBytes("files/routes.json") } else StrelaApi(http, url)
}
