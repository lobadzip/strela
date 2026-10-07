package io.github.lobadzip.strela.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lobadzip.strela.model.GeoPoint
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

data class MapRoute(
    val points: List<GeoPoint>,
    val color: Color,
    val width: Dp = 6.dp,
    val dashed: Boolean = false,
    val casing: Color? = Color.White,
)

/** Lets map content pin itself to coordinates. */
interface MapScope {
    /** Places the element so that its ([alignX], [alignY]) fraction sits on [point]. */
    fun Modifier.anchoredAt(point: GeoPoint, alignX: Float = 0.5f, alignY: Float = 0.5f): Modifier
}

/**
 * A slippy map drawn entirely in Compose: raster tiles on a canvas, routes as paths, markers as
 * ordinary composables. One implementation for Android and the browser, no SDK keys.
 */
@Composable
fun TileMap(
    camera: MapCamera,
    tiles: TileCache,
    style: MapStyle,
    background: Color,
    modifier: Modifier = Modifier,
    routes: List<MapRoute> = emptyList(),
    onTap: ((GeoPoint) -> Unit)? = null,
    content: @Composable MapScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val tileSize = with(density) { 256.dp.toPx() }
    SideEffect { camera.tileSizePx = tileSize }
    val scope = rememberCoroutineScope()
    val mapScope = remember(camera) { MapScopeImpl(camera) }

    Box(
        modifier
            .clipToBounds()
            .background(background)
            .onSizeChanged { camera.viewport = it }
            .pointerInput(camera) {
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    camera.onUserGesture()
                    camera.zoomAround(centroid, zoomChange)
                    camera.panBy(pan.x, pan.y)
                }
            }
            .pointerInput(camera, onTap) {
                detectTapGestures(
                    onDoubleTap = { position ->
                        camera.onUserGesture()
                        val target = camera.toGeo(position)
                        scope.launch { camera.animateTo(target, camera.zoom + 1, durationMs = 350) }
                    },
                    onTap = { position -> onTap?.invoke(camera.toGeo(position)) },
                )
            }
            .pointerInput(camera) {
                // Mouse wheel and trackpad zoom in the browser.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.first()
                            val delta = change.scrollDelta.y
                            if (delta != 0f) {
                                camera.onUserGesture()
                                camera.zoomAround(change.position, 2f.pow(-delta * 0.12f))
                                change.consume()
                            }
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawTiles(camera, tiles, style)
            routes.forEach { drawRoute(camera, it) }
        }
        mapScope.content()
        Text(
            "© участники OpenStreetMap",
            fontSize = 9.sp,
            color = Color(0xFF6B7280),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

private class MapScopeImpl(private val camera: MapCamera) : MapScope {
    override fun Modifier.anchoredAt(point: GeoPoint, alignX: Float, alignY: Float): Modifier =
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(placeable.width, placeable.height) {
                // Reading the camera here re-places the marker on every move without recomposing it.
                val at = camera.toScreen(point)
                placeable.place(
                    (at.x - placeable.width * alignX).roundToInt(),
                    (at.y - placeable.height * alignY).roundToInt(),
                )
            }
        }
}

private fun DrawScope.drawTiles(camera: MapCamera, tiles: TileCache, style: MapStyle) {
    val zoom = camera.zoom
    // Rounding rather than flooring keeps tiles between 0.7× and 1.4× of their size: sharp labels, no mush.
    val z = floor(zoom + 0.5).toInt().coerceIn(0, MapCamera.MAX_ZOOM.toInt())
    val tilesPerSide = 1 shl z
    val tileWorld = camera.tileSizePx * 2.0.pow(zoom - z)
    val worldSize = tileWorld * tilesPerSide

    val left = Mercator.x(camera.center.lon) * worldSize - size.width / 2
    val top = Mercator.y(camera.center.lat) * worldSize - size.height / 2
    val x0 = floor(left / tileWorld).toInt()
    val x1 = floor((left + size.width) / tileWorld).toInt()
    val y0 = floor(top / tileWorld).toInt().coerceAtLeast(0)
    val y1 = floor((top + size.height) / tileWorld).toInt().coerceAtMost(tilesPerSide - 1)
    val side = ceil(tileWorld).toInt() + 1

    for (ty in y0..y1) {
        for (tx in x0..x1) {
            val key = TileKey(z, tx.mod(tilesPerSide), ty)
            val dst = IntOffset((tx * tileWorld - left).roundToInt(), (ty * tileWorld - top).roundToInt())
            val image = tiles[key]
            if (image != null) {
                tiles.touch(key)
                drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), dst, IntSize(side, side),
                    filterQuality = FilterQuality.Medium, colorFilter = style.filter)
                continue
            }
            tiles.request(key)
            // Until the tile arrives, stretch a lower-zoom ancestor that is already here.
            for (levels in 1..4) {
                if (z - levels < 0) break
                val parentKey = key.parent(levels)
                val parent = tiles[parentKey] ?: continue
                val portion = 1 shl levels
                val sub = parent.width / portion
                val src = IntOffset((key.x % portion) * sub, (key.y % portion) * sub)
                drawImage(parent, src, IntSize(sub, sub), dst, IntSize(side, side), filterQuality = FilterQuality.Low, colorFilter = style.filter)
                break
            }
        }
    }
}

private fun DrawScope.drawRoute(camera: MapCamera, route: MapRoute) {
    if (route.points.size < 2) return
    val path = Path()
    route.points.forEachIndexed { i, p ->
        val o: Offset = camera.toScreen(p)
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    val width = route.width.toPx()
    // Dashes this short with round caps read as a dotted line: "the courier is still on the way here".
    val effect = if (route.dashed) PathEffect.dashPathEffect(floatArrayOf(width * 0.2f, width * 1.6f)) else null
    route.casing?.let {
        drawPath(path, it, style = Stroke(width + 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    }
    drawPath(
        path,
        route.color,
        style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect),
    )
}
