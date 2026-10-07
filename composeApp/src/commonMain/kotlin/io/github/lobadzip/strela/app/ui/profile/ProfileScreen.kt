package io.github.lobadzip.strela.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lobadzip.strela.app.AppGraph
import io.github.lobadzip.strela.app.data.SessionState
import io.github.lobadzip.strela.app.platform.rememberLocationPermission
import io.github.lobadzip.strela.app.ui.components.Avatar
import io.github.lobadzip.strela.app.ui.components.Stat
import io.github.lobadzip.strela.app.ui.components.WeekChart
import io.github.lobadzip.strela.app.ui.components.icon
import io.github.lobadzip.strela.app.ui.components.label
import io.github.lobadzip.strela.app.ui.login.ServerDialog
import io.github.lobadzip.strela.app.ui.theme.Strela
import io.github.lobadzip.strela.app.ui.theme.StrelaIcons
import io.github.lobadzip.strela.model.Format
import io.github.lobadzip.strela.model.Order
import io.github.lobadzip.strela.model.OrderStatus

@Composable
fun ProfileScreen(graph: AppGraph, state: SessionState, onBack: () -> Unit) {
    val c = Strela.colors
    val snapshot = state.snapshot
    val courier = snapshot?.courier ?: state.courier ?: return
    var editServer by remember { mutableStateOf(false) }
    val askLocation = rememberLocationPermission { granted -> if (granted) graph.session.setRealGps(true) }

    Column(Modifier.fillMaxSize().background(c.cardRaised).verticalScroll(rememberScrollState())) {
        // Header on the brand gradient: who, how, how well.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(c.ink, Color(0xFF242832))))
                .statusBarsPadding()
                .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(StrelaIcons.Back, "Назад", tint = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(courier.name, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(courier.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(courier.vehicle.icon(), null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(courier.vehicle.label(), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.width(12.dp))
                        Icon(StrelaIcons.Star, null, tint = Color(0xFFFFC53D), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(courier.rating.toString(), style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (snapshot != null) {
                Section("Сегодня") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat(Format.rub(snapshot.stats.earnedKopecks), "заработано", accent = c.brand)
                        Stat(snapshot.stats.delivered.toString(), Format.plural(snapshot.stats.delivered, "доставка", "доставки", "доставок"))
                        Stat(Format.distance(snapshot.stats.distanceMeters), "проехано")
                    }
                }
                Section("Неделя") {
                    val total = snapshot.week.sumOf { it.kopecks }
                    Text(Format.rub(total), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                    Text(
                        "${snapshot.week.sumOf { it.orders }} ${Format.plural(snapshot.week.sumOf { it.orders }, "заказ", "заказа", "заказов")} за 7 дней",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    WeekChart(snapshot.week, snapshot.city.utcOffsetMinutes)
                }
                Section("История смены") {
                    if (snapshot.history.isEmpty()) {
                        Text("Здесь появятся доставленные заказы", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                    snapshot.history.forEachIndexed { i, order ->
                        if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(c.divider))
                        HistoryRow(order, snapshot.city.utcOffsetMinutes)
                    }
                }
            }

            Section("Настройки") {
                if (graph.session.location != null) {
                    SettingRow(
                        icon = StrelaIcons.MyLocation,
                        title = "Реальный GPS",
                        subtitle = if (state.realGps) "Позиция берётся с телефона" else "Демо: маршрут ведёт сервер. Заказы — в центре Москвы",
                        trailing = {
                            Switch(
                                checked = state.realGps,
                                onCheckedChange = { on -> if (on) askLocation() else graph.session.setRealGps(false) },
                                colors = SwitchDefaults.colors(checkedTrackColor = c.brand, checkedThumbColor = Color.White),
                            )
                        },
                    )
                }
                SettingRow(
                    icon = StrelaIcons.Bolt,
                    title = "Создать заказ рядом",
                    subtitle = "Для демо: новый заказ у ближайшего магазина",
                    onClick = { graph.session.createDemoOrder() },
                )
                SettingRow(
                    icon = StrelaIcons.Server,
                    title = if (graph.backend.isLocal) "Город" else "Сервер",
                    subtitle = graph.backend.label + (snapshot?.let { " · время ×${it.speedup.toInt()}" } ?: ""),
                    onClick = if (graph.canChooseServer) ({ editServer = true }) else null,
                )
                SettingRow(
                    icon = StrelaIcons.Logout,
                    title = "Выйти",
                    subtitle = courier.phone,
                    tint = c.danger,
                    onClick = { graph.session.signOut() },
                )
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }

    if (editServer) {
        ServerDialog(
            current = graph.settings.serverUrl,
            suggested = graph.suggestedServerUrl.orEmpty(),
            onDismiss = { editServer = false },
            onChoose = {
                editServer = false
                graph.useServer(it)
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = Strela.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(18.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun HistoryRow(order: Order, utcOffset: Int) {
    val c = Strela.colors
    val ok = order.status == OrderStatus.DELIVERED
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (ok) c.successSoft else c.dangerSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (ok) StrelaIcons.Check else StrelaIcons.Close, null, tint = if (ok) c.success else c.danger, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(order.dropoff.address, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${order.finishedAt?.let { Format.clock(it, utcOffset) } ?: ""} · ${order.pickup.title}" +
                    (order.failureReason?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (ok) "+${Format.rub(order.feeKopecks)}" else "0 ₽",
            style = MaterialTheme.typography.titleSmall,
            color = if (ok) c.textPrimary else c.textTertiary,
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = Strela.colors.textPrimary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Strela.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.cardRaised), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        if (trailing != null) trailing() else if (onClick != null) {
            Icon(StrelaIcons.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
        }
    }
}
