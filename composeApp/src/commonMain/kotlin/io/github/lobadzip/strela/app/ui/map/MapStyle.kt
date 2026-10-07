package io.github.lobadzip.strela.app.ui.map

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Standard OSM tiles are busy and saturated. A colour matrix turns them into a calm base map:
 * soft grey by day, an inverted night map by night, both quiet enough for the orange route to lead.
 */
enum class MapStyle {
    LIGHT,
    DARK,
    ;

    val filter: ColorFilter by lazy {
        val matrix = when (this) {
            LIGHT -> then(lift(0.9f, 0.1f), saturate(0.3f))
            DARK -> then(lift(0.82f, 0.04f), then(saturate(0.25f), then(hue180(), invert())))
        }
        ColorFilter.colorMatrix(ColorMatrix(matrix))
    }
}

private fun invert() = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

/** CSS hue-rotate(180deg): after inverting, water is blue again and parks are green again. */
private fun hue180() = floatArrayOf(
    -0.574f, 1.430f, 0.144f, 0f, 0f,
    0.426f, 0.430f, 0.144f, 0f, 0f,
    0.426f, 1.430f, -0.856f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

private fun saturate(s: Float): FloatArray {
    val r = 0.213f * (1 - s)
    val g = 0.715f * (1 - s)
    val b = 0.072f * (1 - s)
    return floatArrayOf(
        r + s, g, b, 0f, 0f,
        r, g + s, b, 0f, 0f,
        r, g, b + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** Scales every channel by [scale] and adds [lift] of full white: lower contrast, lighter paper. */
private fun lift(scale: Float, lift: Float) = floatArrayOf(
    scale, 0f, 0f, 0f, 255f * lift,
    0f, scale, 0f, 0f, 255f * lift,
    0f, 0f, scale, 0f, 255f * lift,
    0f, 0f, 0f, 1f, 0f,
)

/** [outer] applied after [inner], both 4×5 colour matrices. */
private fun then(outer: FloatArray, inner: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (r in 0 until 4) {
        for (c in 0 until 5) {
            var v = if (c == 4) outer[r * 5 + 4] else 0f
            for (k in 0 until 4) v += outer[r * 5 + k] * inner[k * 5 + c]
            out[r * 5 + c] = v
        }
    }
    return out
}
