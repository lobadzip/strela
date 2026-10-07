package io.github.lobadzip.strela.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lobadzip.strela.app.ui.theme.Strela
import io.github.lobadzip.strela.app.ui.theme.StrelaIcons
import io.github.lobadzip.strela.model.DayEarnings
import io.github.lobadzip.strela.model.Format
import io.github.lobadzip.strela.model.OrderTag
import io.github.lobadzip.strela.model.Vehicle
import kotlinx.coroutines.delay

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Strela.colors.card)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
    ) { content() }
}

@Composable
fun Tag(text: String, color: Color, background: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
fun OrderTagChip(tag: OrderTag) {
    val c = Strela.colors
    when (tag) {
        OrderTag.HOT -> Tag("Горячее", c.danger, c.dangerSoft, StrelaIcons.Fire)
        OrderTag.FRAGILE -> Tag("Хрупкое", c.info, c.infoSoft, StrelaIcons.Fragile)
        OrderTag.HEAVY -> Tag("Тяжёлое", c.warning, c.warningSoft, StrelaIcons.Weight)
        OrderTag.DOCUMENTS -> Tag("Документы", c.textSecondary, c.cardRaised, StrelaIcons.Document)
    }
}

@Composable
fun PaymentChip(cashKopecks: Long) {
    val c = Strela.colors
    if (cashKopecks > 0) {
        Tag("Наличные ${Format.rub(cashKopecks)}", c.warning, c.warningSoft, StrelaIcons.Cash)
    } else {
        Tag("Оплачен", c.success, c.successSoft, StrelaIcons.Check)
    }
}

/** Initials on a colour picked from the name, so the same courier always looks the same. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val palette = listOf(0xFFFF7A45, 0xFF5B8DEF, 0xFF34C38F, 0xFFB57EDC, 0xFFF5B53F, 0xFF4DB6C9)
    val color = Color(palette[(name.hashCode() and 0x7fffffff) % palette.size])
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, style = MaterialTheme.typography.titleSmall, fontSize = (size.value * 0.36f).sp)
    }
}

fun Vehicle.label() = when (this) {
    Vehicle.FOOT -> "пешком"
    Vehicle.BIKE -> "на велосипеде"
    Vehicle.CAR -> "на машине"
}

fun Vehicle.icon() = when (this) {
    Vehicle.FOOT -> StrelaIcons.Walk
    Vehicle.BIKE -> StrelaIcons.Bike
    Vehicle.CAR -> StrelaIcons.Car
}

/** A pulsing dot: green when live, amber while reconnecting. */
@Composable
fun StatusDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Seven bars, today highlighted. Money only, no axes: the courier wants a glance, not a report. */
@Composable
fun WeekChart(days: List<DayEarnings>, utcOffsetMinutes: Int, modifier: Modifier = Modifier) {
    val c = Strela.colors
    val max = (days.maxOfOrNull { it.kopecks } ?: 1L).coerceAtLeast(1L)
    val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    Row(modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        days.forEachIndexed { i, day ->
            val today = i == days.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (day.kopecks >= 100_000) "${day.kopecks / 100_000},${(day.kopecks / 10_000) % 10}к" else "${day.kopecks / 100}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (today) c.brand else c.textTertiary,
                )
                Spacer(Modifier.height(4.dp))
                val fraction = (day.kopecks.toFloat() / max).coerceIn(0.04f, 1f)
                Canvas(Modifier.width(26.dp).height((96 * fraction).dp)) {
                    drawRoundRect(if (today) c.brand else c.cardRaised, Offset.Zero, Size(size.width, size.height), CornerRadius(8.dp.toPx()))
                }
                Spacer(Modifier.height(6.dp))
                // epochDay 0 was a Thursday.
                val weekday = ((day.epochDay + 3) % 7).toInt()
                Text(names[weekday], style = MaterialTheme.typography.labelSmall, color = if (today) c.textPrimary else c.textSecondary)
            }
        }
    }
}

data class Toast(val text: String, val isError: Boolean, val icon: ImageVector? = null, val id: Long)

/** Slides a message in from the top, holds it, slides it away. */
@Composable
fun ToastHost(toast: Toast?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<Toast?>(null) }
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            shown = toast
            delay(3_200)
            shown = null
            delay(300)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = shown != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.statusBarsPadding(),
    ) {
        val current = shown ?: toast ?: return@AnimatedVisibility
        val c = Strela.colors
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(if (current.isError) c.danger else c.ink)
                .clickable { shown = null }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                current.icon ?: if (current.isError) StrelaIcons.Warning else StrelaIcons.Check,
                null,
                tint = if (current.isError) Color.White else c.brand,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(current.text, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A labelled number for stat grids. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, accent: Color = Strela.colors.textPrimary) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Strela.colors.textSecondary)
    }
}

/** Pickup → drop-off with the dotted line between them, as every delivery app draws it. */
@Composable
fun RouteStops(
    fromTitle: String,
    fromAddress: String,
    toTitle: String,
    toAddress: String,
    modifier: Modifier = Modifier,
    fromDone: Boolean = false,
) {
    val c = Strela.colors
    Row(modifier) {
        Column(Modifier.padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (fromDone) c.textTertiary else c.ink))
            Canvas(Modifier.width(2.dp).height(34.dp)) {
                val dash = 3.dp.toPx()
                var y = 4.dp.toPx()
                while (y < size.height - 4.dp.toPx()) {
                    drawCircle(c.textTertiary, radius = 1.dp.toPx(), center = Offset(size.width / 2, y))
                    y += dash * 2
                }
            }
            Box(Modifier.size(12.dp).clip(CircleShape).background(c.brand))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(fromTitle, style = MaterialTheme.typography.titleSmall, color = if (fromDone) c.textTertiary else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fromAddress, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Text(toTitle, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(toAddress, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
