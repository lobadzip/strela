package io.github.lobadzip.strela.app.ui.map

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import io.github.lobadzip.strela.model.GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

/** Web Mercator in unit space: the whole world is the square 0..1, x to the east, y to the south. */
internal object Mercator {
    fun x(lon: Double) = (lon + 180.0) / 360.0
    fun y(lat: Double): Double {
        val s = sin(lat * PI / 180).coerceIn(-0.9999, 0.9999)
        return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
    }
    fun lon(x: Double) = x * 360.0 - 180.0
    fun lat(y: Double) = atan(sinh(PI - 2 * PI * y)) * 180 / PI
}

/** Insets in pixels that the camera keeps clear, e.g. for a bottom sheet over the map. */
data class MapPadding(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f)

/** Where the camera wants to be: a centre and a zoom. */
data class CameraTarget(val center: GeoPoint, val zoom: Double)

/**
 * Where the map is looking. Plain snapshot state, so markers and the canvas follow it without
 * recomposing anything.
 *
 * Screens do not animate the camera themselves. They publish a [CameraTarget] as often as they like
 * (every frame is fine) and the camera eases towards it frame by frame. A new target mid-motion just
 * bends the path: no restarts, no jerks, no zoom "breathing" from animations fighting each other.
 */
@Stable
class MapCamera(center: GeoPoint, zoom: Double) {
    var center by mutableStateOf(center)
        private set
    var zoom by mutableStateOf(zoom)
        private set

    var viewport by mutableStateOf(IntSize.Zero)
        internal set
    internal var tileSizePx by mutableStateOf(256f)

    /** Flips to false when the user drags the map; the camera then stays where they put it. */
    var isFollowing by mutableStateOf(true)

    private val target = MutableStateFlow<CameraTarget?>(null)
    private var lastFrameNanos = -1L
    private var animation: Job? = null
    private var fling: Job? = null

    private fun worldSize(z: Double = zoom) = tileSizePx * 2.0.pow(z)

    fun toScreen(point: GeoPoint): Offset {
        val ws = worldSize()
        val dx = (Mercator.x(point.lon) - Mercator.x(center.lon)) * ws
        val dy = (Mercator.y(point.lat) - Mercator.y(center.lat)) * ws
        return Offset((dx + viewport.width / 2.0).toFloat(), (dy + viewport.height / 2.0).toFloat())
    }

    fun toGeo(screen: Offset): GeoPoint {
        val ws = worldSize()
        val x = Mercator.x(center.lon) + (screen.x - viewport.width / 2.0) / ws
        val y = Mercator.y(center.lat) + (screen.y - viewport.height / 2.0) / ws
        return GeoPoint(Mercator.lat(y), Mercator.lon(x))
    }

    internal fun panBy(dx: Float, dy: Float) {
        val ws = worldSize()
        val x = Mercator.x(center.lon) - dx / ws
        val y = (Mercator.y(center.lat) - dy / ws).coerceIn(0.0, 1.0)
        center = GeoPoint(Mercator.lat(y), Mercator.lon(x))
    }

    /** Zooms keeping the point under [pivot] where it is, the way fingers expect. */
    internal fun zoomAround(pivot: Offset, factor: Float) {
        if (factor == 1f) return
        val anchor = toGeo(pivot)
        zoom = (zoom + log2(factor.toDouble())).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val moved = toScreen(anchor)
        panBy(pivot.x - moved.x, pivot.y - moved.y)
    }

    /** A finger touched the map: everything automatic stops, the user is in charge now. */
    internal fun onUserGesture() {
        animation?.cancel()
        fling?.cancel()
        isFollowing = false
    }

    internal fun stopFling() {
        fling?.cancel()
    }

    /** Keeps the map gliding after a swipe, slowing down like a puck on ice. */
    internal suspend fun fling(velocityX: Float, velocityY: Float) {
        fling?.cancel()
        fling = currentCoroutineContext()[Job]
        var vx = velocityX
        var vy = velocityY
        var last = -1L
        while (hypot(vx, vy) > MIN_FLING_SPEED) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60 else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                panBy(vx * dt, vy * dt)
                val decay = exp(-FLING_FRICTION * dt)
                vx *= decay
                vy *= decay
            }
        }
    }

    fun snapTo(center: GeoPoint, zoom: Double = this.zoom) {
        animation?.cancel()
        this.center = center
        this.zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /** A one-off timed move, for gestures like double-tap zoom. */
    suspend fun animateTo(target: GeoPoint, targetZoom: Double = zoom, durationMs: Int = 700) {
        animation?.cancel()
        animation = currentCoroutineContext()[Job]
        val fromX = Mercator.x(center.lon)
        val fromY = Mercator.y(center.lat)
        val toX = Mercator.x(target.lon)
        val toY = Mercator.y(target.lat)
        val fromZoom = zoom
        val toZoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        animate(0f, 1f, animationSpec = tween(durationMs, easing = FastOutSlowInEasing)) { t, _ ->
            center = GeoPoint(Mercator.lat(fromY + (toY - fromY) * t), Mercator.lon(fromX + (toX - fromX) * t))
            zoom = fromZoom + (toZoom - fromZoom) * t
        }
    }

    /** Where the camera should go while it is following. Cheap; call it as often as the scene changes. */
    fun follow(next: CameraTarget?) {
        target.value = next
    }

    /**
     * Runs for the lifetime of the map: eases towards the latest target while following, and sleeps
     * (no frames requested) once it has arrived, so an idle map costs nothing.
     */
    suspend fun runFollowLoop() {
        combine(target, snapshotFlow { isFollowing }) { t, following -> t.takeIf { following } }
            .collectLatest { goal ->
                if (goal == null) return@collectLatest
                val goalX = Mercator.x(goal.center.lon)
                val goalY = Mercator.y(goal.center.lat)
                val goalZoom = goal.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
                while (true) {
                    val arrived = withFrameNanos { now ->
                        // Real frame time, kept across target changes: a 30 fps phone eases at the same pace.
                        val gap = now - lastFrameNanos
                        val dt = if (lastFrameNanos < 0 || gap > 200_000_000) 1.0 / 60 else gap / 1e9
                        lastFrameNanos = now
                        step(goalX, goalY, goalZoom, dt)
                    }
                    if (arrived) break
                }
            }
    }

    /** One frame of exponential easing. Returns true once the remaining distance is below a pixel. */
    private fun step(goalX: Double, goalY: Double, goalZoom: Double, dt: Double): Boolean {
        val x = Mercator.x(center.lon)
        val y = Mercator.y(center.lat)
        val ws = worldSize()
        val farPx = hypot((goalX - x) * ws, (goalY - y) * ws)
        if (farPx < 0.3 && abs(goalZoom - zoom) < 0.002) {
            center = GeoPoint(Mercator.lat(goalY), Mercator.lon(goalX))
            zoom = goalZoom
            return true
        }
        val k = 1 - exp(-dt / FOLLOW_TIME_S)
        val kz = 1 - exp(-dt / ZOOM_TIME_S)
        center = GeoPoint(Mercator.lat(y + (goalY - y) * k), Mercator.lon(x + (goalX - x) * k))
        zoom += (goalZoom - zoom) * kz
        return false
    }

    /** The centre and zoom that frame [points] inside the viewport minus [padding]. */
    fun framing(points: List<GeoPoint>, padding: MapPadding, maxZoom: Double = 16.5): CameraTarget? {
        if (points.isEmpty() || viewport.width == 0 || viewport.height == 0) return null
        val xs = points.map { Mercator.x(it.lon) }
        val ys = points.map { Mercator.y(it.lat) }
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()
        val width = max(1f, viewport.width - padding.left - padding.right)
        val height = max(1f, viewport.height - padding.top - padding.bottom)

        val spanX = max(maxX - minX, 1e-9)
        val spanY = max(maxY - minY, 1e-9)
        val z = min(log2(width / (spanX * tileSizePx)), log2(height / (spanY * tileSizePx)))
            .coerceIn(MIN_ZOOM, maxZoom)

        // The clear area's centre is off the viewport's centre when the padding is lopsided.
        val ws = tileSizePx * 2.0.pow(z)
        val shiftX = (padding.left + width / 2 - viewport.width / 2.0) / ws
        val shiftY = (padding.top + height / 2 - viewport.height / 2.0) / ws
        val cx = (minX + maxX) / 2 - shiftX
        val cy = (minY + maxY) / 2 - shiftY
        return CameraTarget(GeoPoint(Mercator.lat(cy), Mercator.lon(cx)), z)
    }

    companion object {
        const val MIN_ZOOM = 3.0
        const val MAX_ZOOM = 19.0

        /** Seconds for the camera to cover ~63% of the way to its target: quick, but never a jump. */
        private const val FOLLOW_TIME_S = 0.35
        private const val ZOOM_TIME_S = 0.55
        private const val FLING_FRICTION = 4.5f
        private const val MIN_FLING_SPEED = 40f
    }
}
