package io.github.lobadzip.strela.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import io.github.lobadzip.strela.app.platform.BrowserServices
import io.github.lobadzip.strela.app.platform.BrowserStore
import io.github.lobadzip.strela.app.ui.theme.Strela
import io.github.lobadzip.strela.app.ui.theme.StrelaIcons
import io.github.lobadzip.strela.app.ui.theme.StrelaTheme
import io.github.lobadzip.strela.app.ui.tracking.TrackingScreen
import io.github.lobadzip.strela.app.data.strelaDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import kotlinx.browser.window
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val REPO_URL = "https://github.com/lobadzip/strela"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // The browser sends its own User-Agent and Referer and caches tiles by itself.
    val http = HttpClient(Js) { strelaDefaults() }
    MainScope().launch {
        // Served by the Strela server: talk to it. Served as static files (GitHub Pages): run the city here.
        val origin = window.location.origin + window.location.pathname.substringBefore("/track/").trimEnd('/')
        val hasServer = withTimeoutOrNull(2_500) {
            runCatching { http.get("$origin/health").bodyAsText().trim() == "ok" }.getOrDefault(false)
        } == true
        start(http, if (hasServer) origin else null)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun start(http: HttpClient, serverUrl: String?) {
    val graph = AppGraph(
        store = BrowserStore(),
        platform = BrowserServices(),
        location = null,
        http = http,
        suggestedServerUrl = null,
        webServerUrl = serverUrl,
    )
    val trackCode = Regex("/track/([A-Za-z0-9-]+)").find(window.location.pathname)?.groupValues?.get(1)
    ComposeViewport(document.body!!) {
        when {
            trackCode != null -> StrelaTheme { TrackingScreen(graph, trackCode, Modifier.fillMaxSize()) }
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                // Phones get the courier app itself; desktops get both sides of the delivery at once.
                if (maxWidth >= 1080.dp && maxHeight >= 640.dp) Showcase(graph) else CourierApp(graph)
            }
        }
    }
}

@Composable
private fun Showcase(graph: AppGraph) {
    StrelaTheme(dark = true) {
        val c = Strela.colors
        val session by graph.session.state.collectAsState()
        val myOrder = session.snapshot?.active?.firstOrNull()?.code
        var botOrder by remember { mutableStateOf<String?>(null) }

        // Until the visitor takes an order, follow one of the simulated couriers.
        LaunchedEffect(myOrder) {
            while (myOrder == null) {
                val live = runCatching { graph.backend.liveCodes() }.getOrDefault(emptyList())
                if (botOrder == null || botOrder !in live) botOrder = live.firstOrNull()
                delay(8_000)
            }
        }
        val tracked = myOrder ?: botOrder

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF2A1A14), Color(0xFF0E1014)), radius = 1400f)),
        ) {
            val phoneScale = ((maxHeight - 64.dp) / 844.dp).coerceIn(0.55f, 1f)
            Row(
                Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Intro(Modifier.widthIn(max = 380.dp), mineTracked = myOrder != null, local = graph.backend.isLocal)
                Phone(phoneScale, "Курьер") { CourierApp(graph) }
                Phone(phoneScale, if (myOrder != null) "Клиент · ваш заказ" else "Клиент · курьер-симулятор") {
                    if (tracked != null) {
                        key(tracked) { StrelaTheme { TrackingScreen(graph, tracked, Modifier.fillMaxSize()) } }
                    } else {
                        Waiting()
                    }
                }
            }
        }
    }
}

@Composable
private fun Intro(modifier: Modifier, mineTracked: Boolean, local: Boolean) {
    val c = Strela.colors
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.brand), contentAlignment = Alignment.Center) {
                Icon(StrelaIcons.Navigation, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("strela", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Курьерская доставка на Kotlin Multiplatform",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            lineHeight = 30.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Слева — приложение курьера, справа — страница, которую видит клиент. " +
                "Это один и тот же код на Compose: он же собирается в Android-приложение. " +
                if (local) {
                    "Город работает прямо в этой вкладке: те же правила и симулятор, что на сервере Ktor, " +
                        "курьеры-боты ездят по настоящим улицам Москвы."
                } else {
                    "Сервер на Ktor гоняет курьеров-симуляторов по настоящим улицам Москвы."
                },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.72f),
        )
        Spacer(Modifier.height(24.dp))
        listOf(
            "Войдите как Алексей — код 0000",
            "Выйдите на линию и примите заказ",
            if (mineTracked) "Справа уже ваш заказ — смотрите, как едете" else "Справа сразу появится ваш заказ",
            "У двери: фото, подпись клиента, оплата",
        ).forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = c.brand)
                }
                Spacer(Modifier.width(12.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .clickable { window.open(REPO_URL, "_blank") }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(StrelaIcons.Link, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Код на GitHub", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

/** A phone-shaped window at true phone size, scaled down to fit short screens. */
@Composable
private fun Phone(scale: Float, caption: String, content: @Composable () -> Unit) {
    val width: Dp = 390.dp
    val height: Dp = 844.dp
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width * scale, height * scale), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .requiredSize(width, height)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(40.dp, RoundedCornerShape(52.dp))
                    .clip(RoundedCornerShape(52.dp))
                    .background(Color(0xFF050608))
                    .padding(10.dp)
                    .clip(RoundedCornerShape(44.dp)),
            ) {
                content()
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(caption, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.55f))
    }
}

@Composable
private fun Waiting() {
    val c = Strela.colors
    Column(
        Modifier.fillMaxSize().background(c.card).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Здесь будет страница клиента", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Как только кто-то возьмёт заказ", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}
