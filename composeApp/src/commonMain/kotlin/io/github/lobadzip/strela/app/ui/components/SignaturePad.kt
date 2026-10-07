package io.github.lobadzip.strela.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.lobadzip.strela.app.ui.theme.Strela
import io.github.lobadzip.strela.model.Signature

/** Strokes in pad-relative 0..1 coordinates, so a signature drawn on a phone renders on any screen. */
@Stable
class SignatureState {
    internal val strokes = mutableStateListOf<List<Offset>>()
    internal val current = mutableStateListOf<Offset>()

    val signature: Signature
        get() = Signature((strokes + listOf(current.toList())).filter { it.size > 1 }.map { s -> s.flatMap { listOf(it.x, it.y) } })

    val isBlank: Boolean get() = signature.isBlank

    fun clear() {
        strokes.clear()
        current.clear()
    }
}

@Composable
fun rememberSignatureState() = remember { SignatureState() }

@Composable
fun SignaturePad(state: SignatureState, modifier: Modifier = Modifier, ink: Color = Strela.colors.textPrimary) {
    val c = Strela.colors
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(c.cardRaised)
            .border(1.5.dp, c.divider, RoundedCornerShape(18.dp)),
    ) {
        if (state.strokes.isEmpty() && state.current.isEmpty()) {
            Text(
                "Подпись клиента",
                style = MaterialTheme.typography.bodyLarge,
                color = c.textTertiary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    detectDragGestures(
                        onDragStart = { state.current.add(normalise(it, size.width.toFloat(), size.height.toFloat())) },
                        onDragEnd = {
                            state.strokes.add(state.current.toList())
                            state.current.clear()
                        },
                        onDragCancel = { state.current.clear() },
                        onDrag = { change, _ ->
                            change.consume()
                            state.current.add(normalise(change.position, size.width.toFloat(), size.height.toFloat()))
                        },
                    )
                },
        ) {
            // A signature line, the way paper forms have one.
            drawLine(c.divider, Offset(24.dp.toPx(), size.height * 0.78f), Offset(size.width - 24.dp.toPx(), size.height * 0.78f), 1.5.dp.toPx())
            (state.strokes + listOf(state.current.toList())).forEach { drawStroke(it, ink) }
        }
    }
}

private fun normalise(p: Offset, w: Float, h: Float) = Offset((p.x / w).coerceIn(0f, 1f), (p.y / h).coerceIn(0f, 1f))

private fun DrawScope.drawStroke(points: List<Offset>, color: Color) {
    if (points.size < 2) return
    val path = Path()
    points.forEachIndexed { i, p ->
        val x = p.x * size.width
        val y = p.y * size.height
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
