package io.github.lobadzip.strela.app

import io.github.lobadzip.strela.app.data.CourierSession
import io.github.lobadzip.strela.app.data.Settings
import io.github.lobadzip.strela.app.data.StrelaApi
import io.github.lobadzip.strela.app.data.TrackingSession
import io.github.lobadzip.strela.app.platform.KeyValueStore
import io.github.lobadzip.strela.app.platform.LocationSource
import io.github.lobadzip.strela.app.platform.PlatformServices
import io.github.lobadzip.strela.app.ui.map.TileCache
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Everything with a lifetime longer than a screen. One per process. */
class AppGraph(
    store: KeyValueStore,
    val platform: PlatformServices,
    location: LocationSource?,
    defaultServerUrl: String,
    val http: HttpClient,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings = Settings(store, defaultServerUrl)

    val api = StrelaApi(http) { settings.serverUrl }
    val session = CourierSession(api, settings, scope, location)
    val tiles = TileCache(http, scope)

    fun trackingSession() = TrackingSession(api, scope)
}
