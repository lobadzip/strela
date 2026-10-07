package io.github.lobadzip.strela.app.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lobadzip.strela.app.AppGraph
import io.github.lobadzip.strela.app.ui.components.PrimaryButton
import io.github.lobadzip.strela.app.ui.components.icon
import io.github.lobadzip.strela.app.ui.theme.Strela
import io.github.lobadzip.strela.app.ui.theme.StrelaIcons
import io.github.lobadzip.strela.model.Courier
import kotlinx.coroutines.launch

private enum class Step { PHONE, CODE }

@Composable
fun LoginScreen(graph: AppGraph) {
    val c = Strela.colors
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.PHONE) }
    var phone by remember { mutableStateOf(TextFieldValue("")) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var demoCouriers by remember { mutableStateOf<List<Courier>>(emptyList()) }
    var editServer by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(graph.settings.serverUrl) }

    LaunchedEffect(serverUrl) {
        demoCouriers = runCatching { graph.api.demoCouriers() }.getOrDefault(emptyList())
    }

    fun submit(currentCode: String) {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            error = graph.session.signIn("+7" + digitsOf(phone.text), currentCode)
            loading = false
            if (error != null) code = ""
        }
    }

    // Brand colour behind the card, so its rounded corners sit on the hero instead of a dark gap.
    Column(Modifier.fillMaxSize().background(c.brand)) {
        Hero(Modifier.weight(1f).fillMaxWidth())
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(c.card)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 440.dp)) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val dir = if (targetState == Step.CODE) 1 else -1
                        (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 3 } + fadeOut())
                    },
                ) { current ->
                    when (current) {
                        Step.PHONE -> PhoneStep(
                            phone = phone,
                            onPhone = {
                                val digits = digitsOf(it.text).take(10)
                                val formatted = formatPhone(digits)
                                phone = TextFieldValue(formatted, TextRange(formatted.length))
                                error = null
                            },
                            demoCouriers = demoCouriers,
                            onDemo = { courier ->
                                val digits = digitsOf(courier.phone).drop(1)
                                phone = TextFieldValue(formatPhone(digits), TextRange(formatPhone(digits).length))
                                code = ""
                                step = Step.CODE
                            },
                            onNext = {
                                if (digitsOf(phone.text).length == 10) {
                                    step = Step.CODE
                                } else {
                                    error = "Введите номер полностью"
                                }
                            },
                        )
                        Step.CODE -> CodeStep(
                            phone = "+7 ${phone.text}",
                            code = code,
                            loading = loading,
                            onCode = {
                                code = it.filter(Char::isDigit).take(4)
                                error = null
                                if (code.length == 4) submit(code)
                            },
                            onBack = {
                                step = Step.PHONE
                                error = null
                            },
                            onSubmit = { submit(code) },
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = c.danger, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { editServer = true }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(StrelaIcons.Server, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Сервер: ${serverUrl.removePrefix("http://").removePrefix("https://")} · изменить",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                    )
                }
            }
        }
    }

    if (editServer) {
        ServerDialog(
            current = serverUrl,
            default = graph.settings.defaultServerUrl,
            onDismiss = { editServer = false },
            onSave = {
                graph.settings.serverUrl = it
                serverUrl = graph.settings.serverUrl
                editServer = false
            },
        )
    }
}

@Composable
private fun PhoneStep(
    phone: TextFieldValue,
    onPhone: (TextFieldValue) -> Unit,
    demoCouriers: List<Courier>,
    onDemo: (Courier) -> Unit,
    onNext: () -> Unit,
) {
    val c = Strela.colors
    val focus = remember { FocusRequester() }
    Column {
        Text("Вход для курьеров", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Номер телефона, с которым вы оформлялись", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.cardRaised)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+7", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (phone.text.isEmpty()) {
                    Text("000 000-00-00", style = MaterialTheme.typography.titleLarge, color = c.textTertiary)
                }
                BasicTextField(
                    value = phone,
                    onValueChange = onPhone,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.brand),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        if (demoCouriers.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Демо-вход", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                demoCouriers.forEach { courier ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.5.dp, c.divider, RoundedCornerShape(50))
                            .clickable { onDemo(courier) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(courier.vehicle.icon(), null, tint = c.brand, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(courier.name.substringBefore(' '), style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Получить код", onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CodeStep(
    phone: String,
    code: String,
    loading: Boolean,
    onCode: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val c = Strela.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.cardRaised).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(StrelaIcons.Back, null, tint = c.textPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Код из СМС", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text("Отправили на $phone", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(18.dp))
        BasicTextField(
            value = code,
            onValueChange = onCode,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Box {
                    Box(Modifier.size(1.dp)) { inner() }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) { i ->
                            val filled = i < code.length
                            val active = i == code.length
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(c.cardRaised)
                                    .border(2.dp, if (active) c.brand else Color.Transparent, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (filled) code[i].toString() else "",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = c.textPrimary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        Text("Это демо: подходит код 0000", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Войти", onClick = onSubmit, loading = loading, enabled = code.length == 4, modifier = Modifier.fillMaxWidth())
    }
}

/** Brand block: the wordmark over a city of dotted routes. */
@Composable
private fun Hero(modifier: Modifier) {
    val c = Strela.colors
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFFFF7A3D), c.brand, Color(0xFFD9360A)))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val routes = listOf(
                listOf(Offset(-0.1f, 0.85f), Offset(0.25f, 0.62f), Offset(0.45f, 0.70f), Offset(0.72f, 0.38f), Offset(1.1f, 0.30f)),
                listOf(Offset(0.05f, 0.15f), Offset(0.30f, 0.30f), Offset(0.55f, 0.18f), Offset(0.95f, 0.55f), Offset(1.1f, 0.62f)),
            )
            routes.forEach { pts ->
                val path = Path()
                pts.forEachIndexed { i, p ->
                    val x = p.x * size.width
                    val y = p.y * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path,
                    Color.White.copy(alpha = 0.22f),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 14.dp.toPx()))),
                )
            }
            listOf(Offset(0.25f, 0.62f), Offset(0.72f, 0.38f), Offset(0.55f, 0.18f)).forEach {
                drawCircle(Color.White.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = Offset(it.x * size.width, it.y * size.height))
            }
        }
        Column(Modifier.statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(StrelaIcons.Navigation, null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("strela", style = MaterialTheme.typography.displaySmall, color = Color.White, fontSize = 42.sp)
            Spacer(Modifier.height(4.dp))
            Text("приложение курьера", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
fun ServerDialog(current: String, default: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Адрес сервера") },
        text = {
            Column {
                Text(
                    "Телефон и компьютер с сервером должны быть в одной сети Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Strela.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { value = default }) { Text("По умолчанию: $default") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun digitsOf(text: String) = text.filter(Char::isDigit)

/** 0000000001 → "000 000-00-01", growing as the courier types. */
private fun formatPhone(digits: String): String = buildString {
    digits.forEachIndexed { i, ch ->
        when (i) {
            3 -> append(' ')
            6, 8 -> append('-')
        }
        append(ch)
    }
}
