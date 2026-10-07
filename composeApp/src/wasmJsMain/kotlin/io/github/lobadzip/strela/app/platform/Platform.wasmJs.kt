@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package io.github.lobadzip.strela.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import io.github.lobadzip.strela.model.GeoPoint
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.Promise

class BrowserStore : KeyValueStore {
    // Private windows and locked-down browsers throw on storage access; the app still works, it just forgets.
    override fun get(key: String): String? = runCatching { localStorage.getItem("strela.$key") }.getOrNull()

    override fun set(key: String, value: String?) {
        runCatching {
            if (value == null) localStorage.removeItem("strela.$key") else localStorage.setItem("strela.$key", value)
        }
    }
}

class BrowserServices : PlatformServices {
    override val isWeb = true

    override fun dial(phone: String) {
        window.open("tel:" + phone.filter { it.isDigit() || it == '+' }, "_self")
    }

    override fun openNavigator(point: GeoPoint, label: String) {
        window.open("https://yandex.ru/maps/?rtext=~${point.lat},${point.lon}&rtt=auto", "_blank")
    }

    override fun openUrl(url: String) {
        window.open(url, "_blank")
    }

    override suspend fun demoPhoto(): ByteArray = Base64.decode(drawDemoPhoto().toString())
}

@Composable
actual fun rememberPhotoCapture(onCaptured: (ByteArray) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onCaptured)
    return remember {
        {
            scope.launch {
                val base64 = pickPhoto(1280).await<JsString?>()?.toString() ?: return@launch
                callback(Base64.decode(base64))
            }
        }
    }
}

/** The browser build always drives the courier itself, so there is nothing to ask for. */
@Composable
actual fun rememberLocationPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onResult)
    return remember { { callback(false) } }
}

/** Opens the camera on phones (a file picker on desktops) and returns a downscaled JPEG as base64. */
private fun pickPhoto(maxSide: Int): Promise<JsString?> = js(
    """
    new Promise((resolve) => {
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = 'image/*';
      input.setAttribute('capture', 'environment');
      input.oncancel = () => resolve(null);
      input.onchange = () => {
        const file = input.files && input.files[0];
        if (!file) { resolve(null); return; }
        const img = new Image();
        img.onload = () => {
          const scale = Math.min(1, maxSide / Math.max(img.width, img.height));
          const canvas = document.createElement('canvas');
          canvas.width = Math.round(img.width * scale);
          canvas.height = Math.round(img.height * scale);
          canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
          URL.revokeObjectURL(img.src);
          const data = canvas.toDataURL('image/jpeg', 0.82);
          resolve(data.substring(data.indexOf(',') + 1));
        };
        img.onerror = () => resolve(null);
        img.src = URL.createObjectURL(file);
      };
      input.click();
    })
    """,
)

private fun drawDemoPhoto(): JsString = js(
    """
    (() => {
      const c = document.createElement('canvas');
      c.width = 960; c.height = 720;
      const g = c.getContext('2d');
      const grad = g.createLinearGradient(0, 0, 960, 720);
      grad.addColorStop(0, '#FFB38A'); grad.addColorStop(1, '#FF5A1F');
      g.fillStyle = grad; g.fillRect(0, 0, 960, 720);
      g.fillStyle = '#C98B5A'; g.beginPath(); g.roundRect(300, 220, 360, 280, 24); g.fill();
      g.fillStyle = '#E3A977'; g.beginPath(); g.roundRect(280, 180, 400, 80, 18); g.fill();
      g.fillStyle = '#F5E6D3'; g.fillRect(455, 180, 50, 320);
      g.fillStyle = '#FFFFFF'; g.font = 'bold 44px sans-serif'; g.textAlign = 'center';
      g.fillText('Заказ у двери · демо-фото', 480, 590);
      const data = c.toDataURL('image/jpeg', 0.82);
      return data.substring(data.indexOf(',') + 1);
    })()
    """,
)
