package io.github.lobadzip.strela.app.ui.map

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * OpenStreetMap's own tiles: no key, no account, just attribution and an honest User-Agent.
 * They are recoloured at draw time (see [MapStyle]) so the map recedes and the route stands out.
 */
data class TileKey(val z: Int, val x: Int, val y: Int) {
    fun parent(levels: Int) = TileKey(z - levels, x shr levels, y shr levels)

    internal val url: String get() = "https://tile.openstreetmap.org/$z/$x/$y.png"
}

/**
 * Downloads and keeps map tiles. OSM asks apps to go easy on its servers, hence two downloads at
 * a time and a cache that keeps a few screens' worth of tiles. Loaded tiles live in snapshot state, so the map redraws by itself
 * the moment one arrives. Everything here runs on the main dispatcher; only decoding leaves it.
 */
class TileCache(
    private val client: HttpClient,
    private val scope: CoroutineScope,
    private val capacity: Int = 400,
) {
    private val bitmaps = mutableStateMapOf<TileKey, ImageBitmap>()
    private val recency = LinkedHashSet<TileKey>()
    private val pending = HashSet<TileKey>()
    private val failures = HashMap<TileKey, Int>()
    private val downloads = Semaphore(2)

    /** Reading this inside a draw scope subscribes the drawing to the tile's arrival. */
    operator fun get(key: TileKey): ImageBitmap? = bitmaps[key]

    fun request(key: TileKey) {
        if (key in bitmaps || key in pending || (failures[key] ?: 0) >= MAX_ATTEMPTS) return
        pending += key
        scope.launch {
            try {
                val image = downloads.withPermit {
                    val response = client.get(key.url)
                    check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
                    val bytes = response.readRawBytes()
                    withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
                }
                bitmaps[key] = image
                recency += key
                evict()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                failures[key] = (failures[key] ?: 0) + 1
            } finally {
                pending -= key
            }
        }
    }

    fun touch(key: TileKey) {
        if (recency.remove(key)) recency += key
    }

    private fun evict() {
        while (recency.size > capacity) {
            val oldest = recency.first()
            recency.remove(oldest)
            bitmaps.remove(oldest)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
