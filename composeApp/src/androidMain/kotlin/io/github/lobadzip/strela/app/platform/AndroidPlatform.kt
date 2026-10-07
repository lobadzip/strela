package io.github.lobadzip.strela.app.platform

import android.annotation.SuppressLint
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import io.github.lobadzip.strela.app.AppGraph
import io.github.lobadzip.strela.model.GeoPoint
import io.github.lobadzip.strela.app.data.strelaDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import okhttp3.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object AndroidPlatform {
    /** The app starts as a self-contained demo; [lanServerUrl] is what "connect to a server" suggests. */
    fun createGraph(app: Application, lanServerUrl: String) = AppGraph(
        store = PrefsStore(app),
        platform = AndroidServices(app),
        location = AndroidLocation(app),
        suggestedServerUrl = lanServerUrl,
        http = HttpClient(OkHttp) {
            strelaDefaults()
            // OpenStreetMap's tile policy: say who you are, and keep what you downloaded.
            install(UserAgent) { agent = "Strela/1.0 (Android; +https://github.com/lobadzip/strela)" }
            engine { config { cache(Cache(File(app.cacheDir, "http"), 64L * 1024 * 1024)) } }
        },
    )
}

private class PrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("strela", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun set(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}

private class AndroidServices(private val context: Context) : PlatformServices {
    override val isWeb = false

    override fun dial(phone: String) = start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })))

    /** geo: lets the courier pick Yandex, Google or 2GIS — whichever they actually use. */
    override fun openNavigator(point: GeoPoint, label: String) {
        val uri = Uri.parse("geo:${point.lat},${point.lon}?q=${point.lat},${point.lon}(${Uri.encode(label)})")
        start(Intent(Intent.ACTION_VIEW, uri))
    }

    override fun openUrl(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override suspend fun demoPhoto(): ByteArray = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(960, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 960f, 720f, 0xFFFFB38A.toInt(), 0xFFFF5A1F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 960f, 720f, paint)
        paint.shader = null
        paint.color = 0xFFC98B5A.toInt()
        canvas.drawRoundRect(RectF(300f, 220f, 660f, 500f), 24f, 24f, paint)
        paint.color = 0xFFE3A977.toInt()
        canvas.drawRoundRect(RectF(280f, 180f, 680f, 260f), 18f, 18f, paint)
        paint.color = 0xFFF5E6D3.toInt()
        canvas.drawRect(455f, 180f, 505f, 500f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 44f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Заказ у двери · демо-фото", 480f, 590f, paint)
        bitmap.toJpeg()
    }

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Нет приложения, чтобы это открыть", Toast.LENGTH_SHORT).show()
        }
    }
}

private class AndroidLocation(private val context: Context) : LocationSource {
    @SuppressLint("MissingPermission")
    override fun updates(): Flow<LocationFix> = callbackFlow {
        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            close()
            return@callbackFlow
        }
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { location ->
            val heading = if (location.hasBearing()) location.bearing.toDouble() else null
            trySend(LocationFix(GeoPoint(location.latitude, location.longitude), heading))
        }
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
            .forEach { manager.requestLocationUpdates(it, 2_000L, 5f, listener, Looper.getMainLooper()) }
        awaitClose { manager.removeUpdates(listener) }
    }
}

internal fun Bitmap.toJpeg(quality: Int = 82): ByteArray =
    ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

/** Decodes a camera file at upload size and turns it upright, whatever the camera app thought. */
internal fun loadUpright(file: File, maxSide: Int = 1280): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null

    val rotation = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val scale = (maxSide.toFloat() / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
    if (rotation == 0f && scale == 1f) return decoded
    val matrix = Matrix().apply {
        postRotate(rotation)
        postScale(scale, scale)
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}
