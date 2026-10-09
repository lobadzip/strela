package io.github.lobadzip.strela.app.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.lobadzip.strela.app.ui.theme.Strela
import io.github.lobadzip.strela.app.ui.theme.StrelaIcons
import io.github.lobadzip.strela.model.Geo
import io.github.lobadzip.strela.model.GeoPoint
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Glides between position updates instead of jumping. Each glide lasts as long as the gap between
 * the last two updates, so the marker moves at an even speed and arrives just as the next fix comes
 * in: no stop-and-go. Big jumps (a teleport, a fresh session) snap.
 */
@Composable
fun animatedPosition(target: GeoPoint): State<GeoPoint> {
    val position = remember { mutableStateOf(target) }
    val lastUpdate = remember { mutableStateOf<TimeMark?>(null) }
    LaunchedEffect(target) {
        val since = lastUpdate.value?.elapsedNow()?.inWholeMilliseconds
        lastUpdate.value = TimeSource.Monotonic.markNow()
        val from = position.value
        if (since == null || Geo.distance(from, target) > 2_000) {
            position.value = target
            return@LaunchedEffect
        }
        val duration = since.toInt().coerceIn(MIN_GLIDE_MS, MAX_GLIDE_MS)
        // A new fix mid-glide restarts from wherever the marker is now, so there is never a jump.
        animate(0f, 1f, animationSpec = tween(duration, easing = LinearEasing)) { t, _ ->
            position.value = Geo.lerp(from, target, t.toDouble())
        }
    }
    return position
}

private const val MIN_GLIDE_MS = 250
private const val MAX_GLIDE_MS = 1_500

/** The courier: a dark puck with the brand arrow, breathing so it is easy to find. */
@Composable
fun CourierMarker(heading: Double, modifier: Modifier = Modifier, color: Color = Strela.colors.brand) {
    // Turn the short way round (350° → 10° is 20°, not 340°), and smoothly rather than in steps.
    val rotation = remember { Animatable(heading.toFloat()) }
    LaunchedEffect(heading) {
        val delta = ((heading.toFloat() - rotation.value) % 360f + 540f) % 360f - 180f
        rotation.animateTo(rotation.value + delta, tween(450))
    }
    val pulse = rememberInfiniteTransition()
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart))
    Box(modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(72.dp)) {
            drawCircle(color.copy(alpha = 0.28f * (1 - ring)), radius = size.minDimension / 2 * (0.35f + 0.65f * ring))
        }
        Box(
            Modifier
                .size(40.dp)
                .shadow(8.dp, CircleShape)
                .background(Color(0xFF111318), CircleShape)
                .border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The glyph points north-east, hence the 45° correction.
            Icon(
                StrelaIcons.Navigation,
                null,
                tint = color,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation.value - 45f },
            )
        }
    }
}

/** A teardrop pin with an icon: the shop or the customer's door. */
@Composable
fun PlacePin(icon: ImageVector, color: Color, modifier: Modifier = Modifier, label: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Strela.colors.textPrimary,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .background(Strela.colors.card, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Box(Modifier.size(width = 38.dp, height = 48.dp), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.size(width = 38.dp, height = 48.dp)) {
                val r = size.width / 2
                val tip = Path().apply {
                    moveTo(r - r * 0.55f, r * 1.6f)
                    lineTo(r, size.height)
                    lineTo(r + r * 0.55f, r * 1.6f)
                    close()
                }
                drawCircle(Color.Black.copy(alpha = 0.18f), radius = r * 0.35f, center = Offset(r, size.height - 1.dp.toPx()))
                drawPath(tip, color)
                drawCircle(Color.White, radius = r, center = Offset(r, r))
                drawCircle(color, radius = r - 3.dp.toPx(), center = Offset(r, r))
            }
            Icon(icon, null, tint = Color.White, modifier = Modifier.padding(top = 9.dp).size(20.dp))
        }
    }
}

/** An order in the pool: what it pays, right where it starts. */
@Composable
fun PriceMarker(text: String, selected: Boolean, modifier: Modifier = Modifier) {
    val colors = Strela.colors
    val bg = if (selected) colors.brand else colors.card
    val fg = if (selected) colors.onBrand else colors.textPrimary
    Column(modifier.scale(if (selected) 1.12f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier
                .shadow(6.dp, RoundedCornerShape(50))
                .background(bg, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Canvas(Modifier.size(width = 12.dp, height = 7.dp)) {
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width / 2, size.height); lineTo(size.width, 0f); close() }, bg)
        }
    }
}
