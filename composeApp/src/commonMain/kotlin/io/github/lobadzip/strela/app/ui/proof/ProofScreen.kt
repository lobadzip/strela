package io.github.lobadzip.strela.app.ui.proof

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lobadzip.strela.app.AppGraph
import io.github.lobadzip.strela.app.platform.rememberPhotoCapture
import io.github.lobadzip.strela.app.ui.components.PrimaryButton
import io.github.lobadzip.strela.app.ui.components.SignaturePad
import io.github.lobadzip.strela.app.ui.components.rememberSignatureState
import io.github.lobadzip.strela.app.ui.theme.Strela
import io.github.lobadzip.strela.app.ui.theme.StrelaIcons
import io.github.lobadzip.strela.model.Format
import io.github.lobadzip.strela.model.Order
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Photo, signature, cash: the three things that turn "I was there" into proof. */
@Composable
fun ProofScreen(graph: AppGraph, order: Order, onBack: () -> Unit, onDone: () -> Unit) {
    val c = Strela.colors
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    val signature = rememberSignatureState()
    var cashTaken by remember { mutableStateOf(order.cashToCollectKopecks == 0L) }
    var submitting by remember { mutableStateOf(false) }
    var delivered by remember { mutableStateOf<Order?>(null) }

    fun setPhoto(bytes: ByteArray) {
        photo = bytes
        preview = runCatching { bytes.decodeToImageBitmap() }.getOrNull()
    }
    val capture = rememberPhotoCapture { setPhoto(it) }

    val ready = photo != null && !signature.isBlank && cashTaken

    Box(Modifier.fillMaxSize().background(c.cardRaised)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.card).statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(StrelaIcons.Back, "Назад", tint = c.textPrimary) }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text("Вручение заказа", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text("${order.code} · ${order.dropoff.address}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StepCard(number = 1, title = "Фото у двери", done = photo != null) {
                    if (preview != null) {
                        Box {
                            Image(
                                preview!!,
                                null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)),
                            )
                            Row(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable(onClick = capture)
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(StrelaIcons.Camera, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Переснять", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(2.dp, c.divider, RoundedCornerShape(16.dp))
                                .clickable(onClick = capture),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(Modifier.size(52.dp).clip(CircleShape).background(c.brandSoft), contentAlignment = Alignment.Center) {
                                Icon(StrelaIcons.Camera, null, tint = c.brand, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Сфотографируйте заказ", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        }
                        Text(
                            "Нет камеры под рукой? Взять демо-фото",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.brand,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { scope.launch { setPhoto(graph.platform.demoPhoto()) } }
                                .padding(4.dp),
                        )
                    }
                }

                StepCard(number = 2, title = "Подпись клиента", done = !signature.isBlank) {
                    SignaturePad(signature, Modifier.fillMaxWidth().height(170.dp))
                    if (!signature.isBlank) {
                        Text(
                            "Очистить",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { signature.clear() }.padding(4.dp),
                        )
                    }
                }

                StepCard(number = 3, title = "Оплата", done = cashTaken) {
                    if (order.cashToCollectKopecks > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Получите наличными", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                                Text(Format.rub(order.cashToCollectKopecks), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                            }
                            Switch(
                                checked = cashTaken,
                                onCheckedChange = { cashTaken = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = c.success, checkedThumbColor = Color.White),
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(StrelaIcons.Check, null, tint = c.success, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Оплачен онлайн — деньги брать не нужно", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().background(c.card).navigationBarsPadding().padding(16.dp)) {
                PrimaryButton(
                    if (ready) "Завершить доставку · +${Format.rub(order.feeKopecks)}" else "Заполните три шага",
                    enabled = ready,
                    loading = submitting,
                    color = c.success,
                    onClick = {
                        val bytes = photo ?: return@PrimaryButton
                        submitting = true
                        scope.launch {
                            delivered = graph.session.deliver(order, bytes, signature.signature, order.cashToCollectKopecks)
                            submitting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        delivered?.let { SuccessOverlay(it, onDone) }
    }
}

@Composable
private fun StepCard(number: Int, title: String, done: Boolean, content: @Composable () -> Unit) {
    val c = Strela.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(if (done) c.success else c.cardRaised),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(StrelaIcons.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** A tick that draws itself and the earnings counting up: the small reward after every drop. */
@Composable
private fun SuccessOverlay(order: Order, onDone: () -> Unit) {
    val c = Strela.colors
    val haptics = LocalHapticFeedback.current
    val circle = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    val money = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(250)) }
        circle.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { money.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        tick.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        delay(2_600)
        onDone()
    }

    Box(
        Modifier.fillMaxSize().alpha(fade.value).background(c.card).clickable(onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(Modifier.size(132.dp).scale(circle.value), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(132.dp)) {
                    drawCircle(c.success.copy(alpha = 0.16f))
                    drawCircle(c.success, radius = size.minDimension * 0.36f)
                    val path = Path().apply {
                        moveTo(size.width * 0.34f, size.height * 0.52f)
                        lineTo(size.width * 0.45f, size.height * 0.63f)
                        lineTo(size.width * 0.67f, size.height * 0.40f)
                    }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val partial = Path()
                    measure.getSegment(0f, measure.length * tick.value, partial, true)
                    drawPath(partial, Color.White, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Заказ доставлен", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            val earned = (order.feeKopecks * money.value).toLong() / 100 * 100
            Text("+${Format.rub(earned)}", style = MaterialTheme.typography.displaySmall, color = c.success)
            Spacer(Modifier.height(8.dp))
            Text(
                "${order.code} · ${order.dropoff.address}",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
