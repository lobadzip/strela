package io.github.lobadzip.strela.app.data

import io.github.lobadzip.strela.app.platform.KeyValueStore
import io.github.lobadzip.strela.model.Courier
import kotlinx.serialization.json.Json

/** What survives an app restart: where the server is and who is signed in. */
class Settings(private val store: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Null means no server at all: the demo city runs on the device. */
    var serverUrl: String?
        get() = store[SERVER_URL]?.takeIf { it.isNotBlank() }
        set(value) {
            store[SERVER_URL] = value?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        }

    var token: String?
        get() = store[TOKEN]
        set(value) {
            store[TOKEN] = value
        }

    var courier: Courier?
        get() = store[COURIER]?.let { runCatching { json.decodeFromString<Courier>(it) }.getOrNull() }
        set(value) {
            store[COURIER] = value?.let { json.encodeToString(Courier.serializer(), it) }
        }

    var realGps: Boolean
        get() = store[REAL_GPS] == "1"
        set(value) {
            store[REAL_GPS] = if (value) "1" else null
        }

    private companion object {
        const val SERVER_URL = "server_url"
        const val TOKEN = "token"
        const val COURIER = "courier"
        const val REAL_GPS = "real_gps"
    }
}
