package io.github.lobadzip.strela.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import io.github.lobadzip.strela.app.data.SessionEvent
import io.github.lobadzip.strela.app.data.SessionState
import io.github.lobadzip.strela.app.ui.components.Toast
import io.github.lobadzip.strela.app.ui.components.ToastHost
import io.github.lobadzip.strela.app.ui.home.HomeScreen
import io.github.lobadzip.strela.app.ui.login.LoginScreen
import io.github.lobadzip.strela.app.ui.profile.ProfileScreen
import io.github.lobadzip.strela.app.ui.proof.ProofScreen
import io.github.lobadzip.strela.app.ui.theme.StrelaIcons
import io.github.lobadzip.strela.app.ui.theme.StrelaTheme
import io.github.lobadzip.strela.model.Format
import io.github.lobadzip.strela.model.Order

/** Screens that slide over the map. The map itself never leaves, so it keeps its place and tiles. */
private sealed interface Overlay {
    data object Profile : Overlay
    data class Proof(val order: Order) : Overlay
}

@Composable
fun CourierApp(graph: AppGraph, modifier: Modifier = Modifier) {
    StrelaTheme {
        val state by graph.session.state.collectAsState()
        var toast by remember { mutableStateOf<Toast?>(null) }
        var toastId by remember { mutableStateOf(0L) }

        LaunchedEffect(graph) {
            graph.session.events.collect { event ->
                toastId++
                toast = when (event) {
                    is SessionEvent.Message -> Toast(event.text, event.isError, id = toastId)
                    is SessionEvent.NewOrder -> Toast(
                        "Новый заказ рядом · +${Format.rub(event.order.feeKopecks)}",
                        isError = false,
                        icon = StrelaIcons.Bolt,
                        id = toastId,
                    )
                }
            }
        }

        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (state.courier == null) LoginScreen(graph) else SignedIn(graph, state)
            ToastHost(toast, onDismiss = { toast = null }, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SignedIn(graph: AppGraph, state: SessionState) {
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    BackHandler(enabled = overlay != null) { overlay = null }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            graph = graph,
            state = state,
            onProfile = { overlay = Overlay.Profile },
            onDeliver = { overlay = Overlay.Proof(it) },
        )
        AnimatedContent(
            targetState = overlay,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 }
                } else {
                    slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it }
                }
            },
            contentKey = { it?.let { o -> o::class } },
        ) { current ->
            when (current) {
                null -> Box(Modifier)
                Overlay.Profile -> ProfileScreen(graph, state, onBack = { overlay = null })
                is Overlay.Proof -> ProofScreen(graph, current.order, onBack = { overlay = null }, onDone = { overlay = null })
            }
        }
    }
}
