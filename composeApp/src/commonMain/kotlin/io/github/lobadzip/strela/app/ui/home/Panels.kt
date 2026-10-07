package io.github.lobadzip.strela.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lobadzip.strela.app.ui.components.OrderTagChip
import io.github.lobadzip.strela.app.ui.components.PaymentChip
import io.github.lobadzip.strela.app.ui.components.PrimaryButton
import io.github.lobadzip.strela.app.ui.components.RouteStops
import io.github.lobadzip.strela.app.ui.components.SecondaryButton
import io.github.lobadzip.strela.app.ui.components.Stat
import io.github.lobadzip.strela.app.ui.components.SwipeToConfirm
import io.github.lobadzip.strela.app.ui.components.Tag
import io.github.lobadzip.strela.app.ui.theme.Strela
import io.github.lobadzip.strela.app.ui.theme.StrelaIcons
import io.github.lobadzip.strela.model.CourierSnapshot
import io.github.lobadzip.strela.model.FailureReasons
import io.github.lobadzip.strela.model.Format
import io.github.lobadzip.strela.model.Geo
import io.github.lobadzip.strela.model.Navigation
import io.github.lobadzip.strela.model.Order
import io.github.lobadzip.strela.model.OrderStatus

@Composable
fun OfflinePanel(snapshot: CourierSnapshot, busy: Boolean, onGoOnline: () -> Unit) {
    val c = Strela.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Вы не на линии", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        Spacer(Modifier.height(4.dp))
        val nearby = snapshot.available.size
        Text(
            if (nearby > 0) "Рядом $nearby ${Format.plural(nearby, "заказ", "заказа", "заказов")} — выходите, пока не разобрали"
            else "Выйдите на линию, чтобы получать заказы",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.cardRaised).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat(Format.rub(snapshot.stats.earnedKopecks), "сегодня")
            Stat(snapshot.stats.delivered.toString(), Format.plural(snapshot.stats.delivered, "доставка", "доставки", "доставок"))
            Stat(Format.distance(snapshot.stats.distanceMeters), "в пути")
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            "Выйти на линию",
            onClick = onGoOnline,
            loading = busy,
            icon = StrelaIcons.Power,
            color = c.success,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun PoolPanel(
    snapshot: CourierSnapshot,
    selectedId: String?,
    busy: Set<String>,
    onSelect: (Order) -> Unit,
    onAccept: (Order) -> Unit,
) {
    val c = Strela.colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Заказы рядом", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(c.brandSoft).padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(snapshot.available.size.toString(), style = MaterialTheme.typography.labelLarge, color = c.brand)
            }
        }
        if (snapshot.available.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(StrelaIcons.Clock, null, tint = c.textTertiary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text("Пока тихо. Новые заказы появятся здесь сами", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            return
        }
        // New orders slide in on top; the rest stay put, so a card never jumps from under a finger.
        val known = remember { mutableListOf<String>() }
        val ids = snapshot.available.map { it.id }
        known.retainAll(ids)
        ids.filter { it !in known }.asReversed().forEach { known.add(0, it) }
        val ordered = known.mapNotNull { id -> snapshot.available.firstOrNull { it.id == id } }

        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 620.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ordered, key = { it.id }) { order ->
                OrderCard(
                    order = order,
                    distanceToPickup = Geo.distance(snapshot.courier.position, order.pickup.point).toInt(),
                    utcOffset = snapshot.city.utcOffsetMinutes,
                    selected = order.id == selectedId,
                    accepting = "accept:${order.id}" in busy,
                    onClick = { onSelect(order) },
                    onAccept = { onAccept(order) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderCard(
    order: Order,
    distanceToPickup: Int,
    utcOffset: Int,
    selected: Boolean,
    accepting: Boolean,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Strela.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.brand else c.divider, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("+${Format.rub(order.feeKopecks)}", style = MaterialTheme.typography.headlineSmall, color = c.brand)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.distance(order.distanceMeters), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text("до ${Format.clock(order.deliverBy, utcOffset)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Забрать в ${Format.distance(distanceToPickup)} от вас · ${order.items.sumOf { it.quantity }} ${Format.plural(order.items.sumOf { it.quantity }, "позиция", "позиции", "позиций")}",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        RouteStops(order.pickup.title, order.pickup.address, "Клиент", order.dropoff.address)
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PaymentChip(order.cashToCollectKopecks)
            order.tags.forEach { OrderTagChip(it) }
        }
        AnimatedVisibility(visible = selected) {
            PrimaryButton(
                "Принять заказ",
                onClick = onAccept,
                loading = accepting,
                icon = StrelaIcons.Check,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivePanel(
    order: Order,
    navigation: Navigation?,
    speedup: Double,
    utcOffset: Int,
    busy: Set<String>,
    onCall: () -> Unit,
    onNavigate: () -> Unit,
    onPickUp: () -> Unit,
    onDeliver: () -> Unit,
    onProblem: () -> Unit,
) {
    val c = Strela.colors
    val toPickup = order.status == OrderStatus.ACCEPTED
    val arrived = navigation?.arrived == true
    var showItems by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        // Step header: where we are in the delivery.
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepDots(step = if (toPickup) 0 else 1)
            Spacer(Modifier.width(10.dp))
            Text(
                if (toPickup) "Заберите заказ" else "Доставьте клиенту",
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(order.code, style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (toPickup) order.pickup.title else order.dropoff.address,
            style = MaterialTheme.typography.titleLarge,
            color = c.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (toPickup) order.pickup.address else "${order.customerName} · до ${Format.clock(order.deliverBy, utcOffset)}",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
        order.target.note?.let { note ->
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.cardRaised).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (toPickup) StrelaIcons.Store else StrelaIcons.Home, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(note, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            }
        }
        Spacer(Modifier.height(12.dp))
        EtaRow(navigation, speedup)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Звонок", onClick = onCall, icon = StrelaIcons.Phone, modifier = Modifier.weight(1f))
            SecondaryButton("Маршрут", onClick = onNavigate, icon = StrelaIcons.Route, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.dangerSoft).clickable(onClick = onProblem),
                contentAlignment = Alignment.Center,
            ) {
                Icon(StrelaIcons.Warning, "Проблема с заказом", tint = c.danger, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        val left = navigation?.remainingMeters ?: 0
        if (toPickup) {
            SwipeToConfirm(
                text = if (arrived) "Проведите — заказ у меня" else "До точки ещё ${Format.distance(left)}",
                enabled = arrived,
                loading = "pickup:${order.id}" in busy,
                onConfirm = onPickUp,
            )
        } else {
            SwipeToConfirm(
                text = if (arrived) "Проведите — вручить заказ" else "До клиента ещё ${Format.distance(left)}",
                enabled = arrived,
                color = c.success,
                onConfirm = onDeliver,
            )
        }
        Spacer(Modifier.height(14.dp))
        // Below the fold on purpose: the courier needs the contents at the counter, not on the road.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, c.divider, RoundedCornerShape(16.dp))
                .clickable { showItems = !showItems }
                .padding(14.dp)
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(StrelaIcons.Box, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                val count = order.items.sumOf { it.quantity }
                Text("Состав · $count ${Format.plural(count, "позиция", "позиции", "позиций")}", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.weight(1f))
                PaymentChip(order.cashToCollectKopecks)
                Icon(
                    StrelaIcons.ChevronDown,
                    null,
                    tint = c.textTertiary,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp),
                )
            }
            if (showItems) {
                Spacer(Modifier.height(10.dp))
                order.items.forEach {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("${it.quantity} ×", style = MaterialTheme.typography.bodyMedium, color = c.textTertiary, modifier = Modifier.width(36.dp))
                        Text(it.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    }
                }
                if (order.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { order.tags.forEach { OrderTagChip(it) } }
                }
                Spacer(Modifier.height(8.dp))
                Text("Сумма заказа ${Format.rub(order.totalKopecks)} · ваш доход ${Format.rub(order.feeKopecks)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun EtaRow(navigation: Navigation?, speedup: Double) {
    val c = Strela.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (navigation?.arrived == true) {
            Tag("Вы на месте", c.success, c.successSoft, StrelaIcons.Check)
        } else if (navigation != null) {
            Tag("≈ ${Format.duration(navigation.etaSeconds)}", c.brand, c.brandSoft, StrelaIcons.Clock)
            Spacer(Modifier.width(8.dp))
            Text(Format.distance(navigation.remainingMeters), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        }
        Spacer(Modifier.weight(1f))
        if (speedup > 1.0) {
            Text("демо ×${speedup.toInt()}", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    val c = Strela.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { i ->
            Box(
                Modifier
                    .size(width = if (i == step) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= step) c.brand else c.divider),
            )
        }
    }
}

@Composable
fun FailDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = Strela.colors
    var reason by remember { mutableStateOf(FailureReasons.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text("Не получается доставить?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("Заказ закроется без оплаты. Выберите причину:", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                FailureReasons.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { reason = option }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = reason == option,
                            onClick = { reason = option },
                            colors = RadioButtonDefaults.colors(selectedColor = c.brand),
                        )
                        Text(option, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }) { Text("Закрыть заказ", color = c.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Назад", color = c.textSecondary) } },
    )
}
