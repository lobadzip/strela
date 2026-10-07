package io.github.lobadzip.strela.app.platform

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberPhotoCapture(onCaptured: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onCaptured)
    val file = remember { File(context.cacheDir, "photos/delivery.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.photos", file) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (!saved) return@rememberLauncherForActivityResult
        scope.launch {
            val jpeg = withContext(Dispatchers.Default) { loadUpright(file)?.toJpeg() }
            if (jpeg != null) callback(jpeg)
        }
    }
    return {
        try {
            launcher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Камера недоступна — используйте демо-фото", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
actual fun rememberLocationPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        callback(result.values.any { it })
    }
    return {
        launcher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}
