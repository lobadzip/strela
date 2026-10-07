package io.github.lobadzip.strela.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lobadzip.strela.app.AppGraph
import io.github.lobadzip.strela.app.data.Connection
import io.github.lobadzip.strela.app.data.SessionState
import io.github.lobadzip.strela.app.ui.components.Avatar
import io.github.lobadzip.strela.app.ui.components.MapButton
import io.github.lobadzip.strela.app.ui.components.StatusDot
import io.github.lobadzip.strela.app.ui.map.CourierMarker
import io.github.lobadzip.strela.app.ui.map.MapCamera
import io.github.lobadzip.strela.app.ui.map.MapPadding
import io.github.lobadzip.strela.app.ui.map.MapRoute
import io.github.lobadzip.strela.app.ui.map.MapScope
import io.github.lobadzip.strela.app.ui.map.MapStyle
import io.github.lobadzip.strela.app.ui.map.PlacePin
import io.github.lobadzip.strela.app.ui.map.PriceMarker
import io.github.lobadzip.strela.app.ui.map.TileMap
import io.github.lobadzip.strela.app.ui.map.animatedPosition
import io.github.lobadzip.strela.app.ui.theme.Strela
import io.github.lobadzip.strela.app.ui.theme.StrelaIcons
import io.github.lobadzip.strela.model.CourierSnapshot
import io.github.lobadzip.strela.model.Format
import io.github.lobadzip.strela.model.GeoPoint
import io.github.lobadzip.strela.model.Order
import io.github.lobadzip.strela.model.OrderStatus
import kotlinx.coroutines.launch

private enum class Mode { LOADING, OFFLINE, POOL, ACTIVE }

private fun CourierSnapshot?.mode() = when {
    this == null -> Mode.LOADING
    active.isNotEmpty() -> Mode.ACTIVE
    !courier.online -> Mode.OFFLINE
    else -> Mode.POOL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    graph: AppGraph,
    state: SessionState,
    onProfile: () -> Unit,
    onDeliver: (Order) -> Unit,
) {
    val c = Strela.colors
    val snapshot = state.snapshot
    val mode = snapshot.mode()
    val active = snapshot?.active?.firstOrNull()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val camera = remember { MapCamera(snapshot?.courier?.position ?: GeoPoint(55.7539, 37.6208), 14.0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = snapshot?.available?.firstOrNull { it.id == selectedId }
    var showFailDialog by remember { mutableStateOf(false) }

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffold = rememberBottomSheetScaffoldState(sheetState)
    val navBar = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val statusBar = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val peek: Dp = when (mode) {
        Mode.LOADING -> 160.dp
        Mode.OFFLINE -> 300.dp
        Mode.POOL -> 330.dp
        Mode.ACTIVE -> 410.dp
    } + navBar

    // Keep the camera's clear area above the sheet wherever the courier left it.
    var mapHeight by remember { mutableStateOf(0) }
    val sheetTop = runCatching { sheetState.requireOffset() }.getOrNull()
    val covered = with(density) {
        if (sheetTop != null && mapHeight > 0) (mapHeight - sheetTop).coerceAtLeast(peek.toPx()) else peek.toPx()
    }
    val padding = with(density) {
        MapPadding(left = 40.dp.toPx(), top = (statusBar + 110.dp).toPx(), right = 40.dp.toPx(), bottom = covered + 56.dp.toPx())
    }

    // What the camera should keep in frame right now.
    val focus: List<GeoPoint> = when {
        snapshot == null -> emptyList()
        active != null -> listOf(snapshot.courier.position, active.target.point) +
            if (active.status == OrderStatus.ACCEPTED) listOf(active.dropoff.point) else emptyList()
        selected != null -> listOf(snapshot.courier.position, selected.pickup.point, selected.dropoff.point)
        else -> listOf(snapshot.courier.position) + snapshot.available.take(5).map { it.pickup.point }
    }
    val focusKey = listOf(mode, selectedId, active?.id, active?.status)

    // Re-frame when the situation changes; while following, keep re-framing as the courier moves.
    LaunchedEffect(focusKey, camera.viewport, peek, sheetState.currentValue) {
        if (focus.isNotEmpty()) {
            camera.isFollowing = true
            camera.fit(focus, padding, maxZoom = 16.5)
        }
    }
    LaunchedEffect(snapshot?.courier?.position) {
        if (mode == Mode.ACTIVE && camera.isFollowing && focus.isNotEmpty()) {
            camera.fit(focus, padding, maxZoom = 17.0)
        }
    }

    val routes = buildList {
        val nav = snapshot?.navigation
        when {
            active != null && active.status == OrderStatus.ACCEPTED -> {
                add(MapRoute(active.route, c.textTertiary.copy(alpha = 0.7f), width = 5.dp, casing = c.routeCasing))
                nav?.let { add(MapRoute(it.remaining, c.brand, width = 7.dp, dashed = true, casing = null)) }
            }
            active != null -> nav?.let { add(MapRoute(it.remaining, c.brand, width = 6.dp, casing = c.routeCasing)) }
            selected != null -> add(MapRoute(selected.route, c.brand, width = 5.dp, casing = c.routeCasing))
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffold,
        sheetPeekHeight = peek,
        sheetContainerColor = c.card,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetShadowElevation = 18.dp,
        sheetDragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 5.dp).clip(CircleShape).background(c.divider))
        },
        containerColor = c.mapBackground,
        sheetContent = {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it },
            ) { current ->
                Column(Modifier.fillMaxWidth().padding(bottom = navBar)) {
                    when (current) {
                        Mode.LOADING -> LoadingPanel()
                        Mode.OFFLINE -> OfflinePanel(snapshot!!, busy = "shift" in state.busy) { graph.session.setOnline(true) }
                        Mode.POOL -> PoolPanel(
                            snapshot = snapshot!!,
                            selectedId = selectedId,
                            busy = state.busy,
                            onSelect = { selectedId = if (selectedId == it.id) null else it.id },
                            onAccept = { graph.session.accept(it) },
                        )
                        Mode.ACTIVE -> snapshot?.active?.firstOrNull()?.let { order ->
                            ActivePanel(
                                order = order,
                                navigation = snapshot.navigation,
                                speedup = snapshot.speedup,
                                utcOffset = snapshot.city.utcOffsetMinutes,
                                busy = state.busy,
                                onCall = { graph.platform.dial(order.customerPhone) },
                                onNavigate = { graph.platform.openNavigator(order.target.point, order.target.address) },
                                onPickUp = { graph.session.pickUp(order) },
                                onDeliver = { onDeliver(order) },
                                onProblem = { showFailDialog = true },
                            )
                        }
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().onSizeChanged { mapHeight = it.height }) {
            TileMap(
                camera = camera,
                tiles = graph.tiles,
                style = if (c.isDark) MapStyle.DARK else MapStyle.LIGHT,
                background = c.mapBackground,
                routes = routes,
                modifier = Modifier.fillMaxSize(),
                onTap = { if (mode == Mode.POOL) selectedId = null },
            ) {
                if (snapshot != null) {
                    when {
                        active != null -> {
                            if (active.status == OrderStatus.ACCEPTED) {
                                PlacePin(StrelaIcons.Store, c.ink, Modifier.anchoredAt(active.pickup.point, 0.5f, 1f))
                            }
                            PlacePin(StrelaIcons.Home, c.brand, Modifier.anchoredAt(active.dropoff.point, 0.5f, 1f))
                        }
                        else -> {
                            selected?.let {
                                PlacePin(StrelaIcons.Home, c.brand, Modifier.anchoredAt(it.dropoff.point, 0.5f, 1f))
                            }
                            snapshot.available.forEach { order ->
                                PriceMarker(
                                    "+${Format.rub(order.feeKopecks)}",
                                    selected = order.id == selectedId,
                                    modifier = Modifier
                                        .anchoredAt(order.pickup.point, 0.5f, 1f)
                                        .clickable(MutableInteractionSource(), indication = null) {
                                            selectedId = order.id
                                        },
                                )
                            }
                        }
                    }
                    AnimatedCourier(snapshot.courier.position, snapshot.courier.heading)
                }
            }

            TopBar(
                state = state,
                onProfile = onProfile,
                onToggleOnline = {
                    val online = snapshot?.courier?.online ?: return@TopBar
                    graph.session.setOnline(!online)
                },
            )

            AnimatedVisibility(
                visible = !camera.isFollowing && focus.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = with(density) { covered.toDp() } + 16.dp),
            ) {
                MapButton(StrelaIcons.MyLocation, onClick = {
                    camera.isFollowing = true
                    scope.launch { camera.fit(focus, padding, maxZoom = 16.5) }
                })
            }
        }
    }

    if (showFailDialog && active != null) {
        FailDialog(
            onDismiss = { showFailDialog = false },
            onConfirm = { reason ->
                showFailDialog = false
                graph.session.fail(active, reason)
            },
        )
    }
}

@Composable
private fun MapScope.AnimatedCourier(position: GeoPoint, heading: Double) {
    val animated by animatedPosition(position)
    CourierMarker(heading, Modifier.anchoredAt(animated))
}

@Composable
private fun TopBar(state: SessionState, onProfile: () -> Unit, onToggleOnline: () -> Unit) {
    val c = Strela.colors
    val snapshot = state.snapshot
    val courier = state.courier ?: return
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.shadow(10.dp, CircleShape).clip(CircleShape).background(c.card).clickable(onClick = onProfile).padding(3.dp),
                ) {
                    Avatar(courier.name, size = 42.dp)
                }
                Spacer(Modifier.weight(1f))
                if (snapshot != null) {
                    val online = snapshot.courier.online
                    Row(
                        Modifier
                            .shadow(10.dp, RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .background(if (online) c.ink else c.card)
                            .clickable(enabled = snapshot.active.isEmpty() && "shift" !in state.busy, onClick = onToggleOnline)
                            .padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(if (online) c.success else c.textTertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (online) "На линии" else "Не на линии",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (online) Color.White else c.textPrimary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .shadow(10.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(c.card)
                        .clickable(onClick = onProfile)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(StrelaIcons.Wallet, null, tint = c.brand, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        Format.rub(snapshot?.stats?.earnedKopecks ?: 0),
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textPrimary,
                    )
                }
            }
        }
        AnimatedVisibility(visible = state.connection == Connection.RECONNECTING) {
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.warningSoft)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(StrelaIcons.Refresh, null, tint = c.warning, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Нет связи с сервером — переподключаемся", style = MaterialTheme.typography.labelMedium, color = c.warning)
            }
        }
    }
}

@Composable
private fun LoadingPanel() {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Загружаем смену…", style = MaterialTheme.typography.titleMedium, color = Strela.colors.textSecondary)
        Spacer(Modifier.height(80.dp))
    }
}
