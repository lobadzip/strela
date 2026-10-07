package io.github.lobadzip.strela.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.lobadzip.strela.app.ui.theme.Strela
import io.github.lobadzip.strela.app.ui.theme.StrelaIcons
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    color: Color = Strela.colors.brand,
    contentColor: Color = Color.White,
) {
    val active = enabled && !loading
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) color else Strela.colors.cardRaised)
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), color = contentColor, strokeWidth = 2.5.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) {
                    Icon(icon, null, tint = if (enabled) contentColor else Strela.colors.textTertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) contentColor else Strela.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = Strela.colors.textPrimary,
) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Strela.colors.cardRaised)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1)
        }
    }
}

/** Round floating button for the map: recenter, zoom, back. */
@Composable
fun MapButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Strela.colors.textPrimary) {
    Box(
        modifier
            .size(48.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Strela.colors.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * The courier's main control: a deliberate swipe, so a pocket tap never marks an order picked up.
 * Disabled, it shows why ("ещё 600 м").
 */
@Composable
fun SwipeToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color = Strela.colors.brand,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val confirm by rememberUpdatedState(onConfirm)
    val offset = remember { Animatable(0f) }
    val thumb = 56.dp
    val pad = 4.dp

    // Whenever the action finishes (or the label changes), the thumb comes home.
    LaunchedEffect(loading, text) {
        if (!loading) offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    val shimmer = rememberInfiniteTransition()
    val sweep by shimmer.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart))

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(thumb + pad * 2)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) color else Strela.colors.cardRaised),
    ) {
        val density = LocalDensity.current
        val max = with(density) { (maxWidth - thumb - pad * 2).toPx() }.coerceAtLeast(1f)
        val progress = (offset.value / max).coerceIn(0f, 1f)
        val labelColor = if (enabled) Color.White else Strela.colors.textTertiary

        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = thumb + 12.dp, end = 16.dp)
                .alpha(1f - progress * 1.4f),
        )
        if (enabled && !loading) {
            // A soft highlight runs across the track: "swipe me".
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            (sweep - 0.15f).coerceIn(0f, 1f) to Color.Transparent,
                            sweep.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.18f),
                            (sweep + 0.15f).coerceIn(0f, 1f) to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .padding(pad)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(thumb)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Strela.colors.card)
                .draggable(
                    enabled = enabled && !loading,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, max)) }
                    },
                    onDragStopped = {
                        if (offset.value > max * 0.82f) {
                            offset.animateTo(max)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            confirm()
                        } else {
                            offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = color, strokeWidth = 2.5.dp)
            } else {
                Icon(
                    StrelaIcons.ChevronRight,
                    null,
                    tint = if (enabled) color else Strela.colors.textTertiary,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}
