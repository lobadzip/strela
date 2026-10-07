package io.github.lobadzip.strela.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lobadzip.strela.app.resources.Res
import io.github.lobadzip.strela.app.resources.manrope_bold
import io.github.lobadzip.strela.app.resources.manrope_extrabold
import io.github.lobadzip.strela.app.resources.manrope_medium
import io.github.lobadzip.strela.app.resources.manrope_regular
import io.github.lobadzip.strela.app.resources.manrope_semibold
import io.github.lobadzip.strela.app.resources.unbounded_bold
import io.github.lobadzip.strela.app.resources.unbounded_semibold
import org.jetbrains.compose.resources.Font

/** Colours Material does not have a slot for. */
@Immutable
data class StrelaColors(
    val brand: Color,
    val onBrand: Color,
    val brandSoft: Color,
    val ink: Color,
    val card: Color,
    val cardRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val mapBackground: Color,
    val routeCasing: Color,
    val isDark: Boolean,
)

private val Brand = Color(0xFFFF5A1F)

private val LightStrela = StrelaColors(
    brand = Brand,
    onBrand = Color.White,
    brandSoft = Color(0xFFFFEDE5),
    ink = Color(0xFF111318),
    card = Color.White,
    cardRaised = Color(0xFFF4F5F7),
    textPrimary = Color(0xFF111318),
    textSecondary = Color(0xFF5F6673),
    textTertiary = Color(0xFF9AA1AD),
    divider = Color(0xFFE8EAEE),
    success = Color(0xFF0FA968),
    successSoft = Color(0xFFE3F7EE),
    warning = Color(0xFFE88A00),
    warningSoft = Color(0xFFFFF3DD),
    danger = Color(0xFFE5383B),
    dangerSoft = Color(0xFFFDE8E8),
    info = Color(0xFF2E7CF6),
    infoSoft = Color(0xFFE6F0FF),
    mapBackground = Color(0xFFE9E9E7),
    routeCasing = Color.White,
    isDark = false,
)

private val DarkStrela = StrelaColors(
    brand = Brand,
    onBrand = Color.White,
    brandSoft = Color(0xFF3A1E14),
    ink = Color(0xFF0B0D10),
    card = Color(0xFF181B21),
    cardRaised = Color(0xFF22262E),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFFA3AAB6),
    textTertiary = Color(0xFF6B7280),
    divider = Color(0xFF2A2F38),
    success = Color(0xFF2FD18B),
    successSoft = Color(0xFF12291F),
    warning = Color(0xFFFFB020),
    warningSoft = Color(0xFF2E2410),
    danger = Color(0xFFFF5C5F),
    dangerSoft = Color(0xFF331718),
    info = Color(0xFF5B9BFF),
    infoSoft = Color(0xFF15233A),
    mapBackground = Color(0xFF1F2124),
    routeCasing = Color(0xFF0B0D10),
    isDark = true,
)

val LocalStrelaColors = staticCompositionLocalOf { LightStrela }

object Strela {
    val colors: StrelaColors
        @Composable get() = LocalStrelaColors.current
}

private fun scheme(c: StrelaColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.brand,
        onPrimary = c.onBrand,
        primaryContainer = c.brandSoft,
        onPrimaryContainer = c.brand,
        secondary = c.ink,
        background = if (c.isDark) Color(0xFF0E1014) else Color(0xFFF4F5F7),
        onBackground = c.textPrimary,
        surface = c.card,
        onSurface = c.textPrimary,
        surfaceVariant = c.cardRaised,
        onSurfaceVariant = c.textSecondary,
        surfaceContainerLow = c.card,
        surfaceContainer = c.card,
        surfaceContainerHigh = c.card,
        outline = c.divider,
        outlineVariant = c.divider,
        error = c.danger,
    )
}

@Composable
private fun manrope() = FontFamily(
    Font(Res.font.manrope_regular, FontWeight.Normal),
    Font(Res.font.manrope_medium, FontWeight.Medium),
    Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    Font(Res.font.manrope_bold, FontWeight.Bold),
    Font(Res.font.manrope_extrabold, FontWeight.ExtraBold),
)

@Composable
private fun unbounded() = FontFamily(
    Font(Res.font.unbounded_semibold, FontWeight.SemiBold),
    Font(Res.font.unbounded_bold, FontWeight.Bold),
)

@Composable
fun StrelaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkStrela else LightStrela
    val body = manrope()
    // The display face is wide and confident: money, big numbers, headlines.
    val display = unbounded()

    val typography = Typography(
        displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
        headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
    CompositionLocalProvider(LocalStrelaColors provides colors) {
        MaterialTheme(colorScheme = scheme(colors), typography = typography, shapes = shapes, content = content)
    }
}
