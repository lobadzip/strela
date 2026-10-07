package io.github.lobadzip.strela.app.ui.tracking

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lobadzip.strela.app.AppGraph
import io.github.lobadzip.strela.app.ui.components.Avatar
import io.github.lobadzip.strela.app.ui.components.Tag
import io.github.lobadzip.strela.app.ui.components.label
import io.github.lobadzip.strela.app.ui.map.CourierMarker
import io.github.lobadzip.strela.app.ui.map.MapCamera
import io.github.lobadzip.strela.app.ui.map.MapPadding
import io.github.lobadzip.strela.app.ui.map.MapRoute
import io.github.lobadzip.strela.app.ui.map.MapScope
import io.github.lobadzip.strela.app.ui.map.MapStyle
import io.github.lobadzip.strela.app.ui.map.PlacePin
import io.github.lobadzip.strela.app.ui.map.TileMap
import io.github.lobadzip.strela.app.ui.map.animatedPosition
import io.github.lobadzip.strela.app.ui.theme.Strela
import io.github.lobadzip.strela.app.ui.theme.StrelaIcons
import io.github.lobadzip.strela.model.Format
import io.github.lobadzip.strela.model.GeoPoint
import io.github.lobadzip.strela.model.OrderStatus
import io.github.lobadzip.strela.model.TrackingView
import org.jetbrains.compose.resources.decodeToImageBitmap

/** What the customer opens from the SMS link: where the courier is and when they will ring. */
@Composable
fun TrackingScreen(graph: AppGraph, code: String, modifier: Modifier = Modifier) {
    val session = remember(code) { graph.trackingSession().also { it.follow(code) } }
    DisposableEffect(session) { onDispose { session.stop() } }
    val state by session.state.collectAsState()
    val c = Strela.colors
    val view = state.view

    Box(modifier.background(c.mapBackground)) {
        when {
            view != null -> TrackingContent(graph, view)
            state.error != null -> Message(state.error!!)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.brand) }
        }
    }
}

@Composable
private fun TrackingContent(graph: AppGraph, view: TrackingView) {
    val c = Strela.colors
    val density = LocalDensity.current
    val camera = remember { MapCamera(view.dropoff.point, 14.0) }
    var panelHeight by remember { mutableStateOf(0) }

    val focus = buildList {
        view.courier?.let { add(it.position) }
        if (view.status == OrderStatus.AVAILABLE || view.status == OrderStatus.ACCEPTED) add(view.pickup.point)
        add(view.dropoff.point)
    }
    val padding = with(density) { MapPadding(48.dp.toPx(), 110.dp.toPx(), 48.dp.toPx(), panelHeight + 30.dp.toPx()) }
    LaunchedEffect(view.status, camera.viewport, panelHeight) {
        camera.isFollowing = true
        camera.fit(focus, padding, maxZoom = 16.0)
    }
    LaunchedEffect(view.courier?.position) { if (camera.isFollowing) camera.fit(focus, padding, maxZoom = 16.5) }

    val routes = when (view.status) {
        OrderStatus.AVAILABLE -> listOf(MapRoute(view.route, c.textTertiary, width = 5.dp, casing = c.routeCasing))
        OrderStatus.ACCEPTED -> listOf(
            MapRoute(view.route, c.textTertiary.copy(alpha = 0.7f), width = 5.dp, casing = c.routeCasing),
            MapRoute(view.remainingRoute, c.brand, width = 7.dp, dashed = true, casing = null),
        )
        OrderStatus.PICKED_UP -> listOf(MapRoute(view.remainingRoute, c.brand, width = 6.dp, casing = c.routeCasing))
        else -> emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        TileMap(
            camera = camera,
            tiles = graph.tiles,
            style = if (c.isDark) MapStyle.DARK else MapStyle.LIGHT,
            background = c.mapBackground,
            routes = routes,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (view.status != OrderStatus.PICKED_UP && view.status != OrderStatus.DELIVERED) {
                PlacePin(StrelaIcons.Store, c.ink, Modifier.anchoredAt(view.pickup.point, 0.5f, 1f))
            }
            PlacePin(StrelaIcons.Home, c.brand, Modifier.anchoredAt(view.dropoff.point, 0.5f, 1f), label = "Вы")
            if (view.courier != null && (view.status == OrderStatus.ACCEPTED || view.status == OrderStatus.PICKED_UP)) {
                Courier(view.courier!!.position, view.courier!!.heading)
            }
        }

        // Brand strip on top.
        Row(
            Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .shadow(10.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(c.card)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(c.brand), contentAlignment = Alignment.Center) {
                Icon(StrelaIcons.Navigation, null, tint = c.onBrand, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("strela", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.width(10.dp))
            Text("заказ ${view.code}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .onSizeChanged { panelHeight = it.height }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.card)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(20.dp)
                .animateContentSize(),
        ) {
            StatusBlock(view)
            Spacer(Modifier.height(16.dp))
            Progress(view.status)
            view.courier?.let { courier ->
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(courier.firstName, size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Курьер ${courier.firstName}", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(courier.vehicle.label(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Tag("★ ${courier.rating}", c.warning, c.warningSoft)
                }
            }
            Spacer(Modifier.height(18.dp))
            Timeline(view)
            if (view.proofPhotoUrl != null) {
                Spacer(Modifier.height(16.dp))
                ProofPhoto(graph, view.proofPhotoUrl!!)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            Spacer(Modifier.height(14.dp))
            Text("Из «${view.pickup.title.substringAfter('«').substringBefore('»')}»", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            view.items.forEach {
                Text("${it.quantity} × ${it.name}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Итого ${Format.rub(view.totalKopecks)}" + if (view.cashToCollectKopecks > 0) " · оплата курьеру наличными" else " · оплачено",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
            )
            if (view.speedup > 1) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Демо: время ускорено ×${view.speedup.toInt()}, курьеры — симуляция",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun MapScope.Courier(position: GeoPoint, heading: Double) {
    val animated by animatedPosition(position)
    CourierMarker(heading, Modifier.anchoredAt(animated))
}

@Composable
private fun StatusBlock(view: TrackingView) {
    val c = Strela.colors
    val offset = view.city.utcOffsetMinutes
    val (title, subtitle) = when (view.status) {
        OrderStatus.AVAILABLE -> "Ищем курьера" to "Обычно это пара минут"
        OrderStatus.ACCEPTED -> "Курьер едет за заказом" to "${view.pickup.title}, ${view.pickup.address}. Оттуда сразу к вам"
        OrderStatus.PICKED_UP -> "Курьер едет к вам" to view.dropoff.address
        OrderStatus.DELIVERED -> "Заказ доставлен" to "в ${view.finishedAt?.let { Format.clock(it, offset) } ?: ""}, спасибо!"
        OrderStatus.FAILED -> "Доставка не состоялась" to (view.failureReason ?: "")
    }
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        view.etaSeconds?.let { eta ->
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.duration(eta), style = MaterialTheme.typography.headlineMedium, color = c.brand, maxLines = 1)
                Text(
                    "около ${Format.clock(view.serverTime + eta * 1000L, offset)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
    }
}

/** Four segments; the current one breathes. */
@Composable
private fun Progress(status: OrderStatus) {
    val c = Strela.colors
    val step = when (status) {
        OrderStatus.AVAILABLE -> 0
        OrderStatus.ACCEPTED -> 1
        OrderStatus.PICKED_UP -> 2
        OrderStatus.DELIVERED -> 4
        OrderStatus.FAILED -> -1
    }
    val pulse = rememberInfiniteTransition()
    val glow by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse))
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val gap = 6.dp.toPx()
        val width = (size.width - gap * 3) / 4
        repeat(4) { i ->
            val color = when {
                status == OrderStatus.FAILED -> c.danger.copy(alpha = 0.4f)
                i < step -> c.brand
                i == step -> c.brand.copy(alpha = glow)
                else -> c.divider
            }
            drawRoundRect(color, Offset(i * (width + gap), 0f), Size(width, size.height), CornerRadius(size.height / 2))
        }
    }
}

@Composable
private fun Timeline(view: TrackingView) {
    val c = Strela.colors
    val offset = view.city.utcOffsetMinutes
    val steps = listOf(
        "Заказ оформлен" to view.createdAt,
        "Курьер назначен" to view.acceptedAt,
        "Курьер забрал заказ" to view.pickedUpAt,
        (if (view.status == OrderStatus.FAILED) "Не доставлен" else "Доставлен") to view.finishedAt,
    )
    Column {
        steps.forEachIndexed { i, (label, time) ->
            val done = time != null
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(if (done) c.brand else c.cardRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(StrelaIcons.Check, null, tint = c.onBrand, modifier = Modifier.size(12.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (done) c.textPrimary else c.textTertiary, modifier = Modifier.weight(1f))
                Text(time?.let { Format.clock(it, offset) } ?: "—", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
            if (i < steps.lastIndex) Spacer(Modifier.height(0.dp))
        }
    }
}

@Composable
private fun ProofPhoto(graph: AppGraph, url: String) {
    val image = remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        image.value = graph.backend.photo(url)?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }
    image.value?.let {
        androidx.compose.foundation.Image(
            it,
            "Фото доставки",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp)),
        )
    }
}

@Composable
private fun Message(text: String) {
    val c = Strela.colors
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(StrelaIcons.Box, null, tint = c.textTertiary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Проверьте ссылку из СМС", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}
