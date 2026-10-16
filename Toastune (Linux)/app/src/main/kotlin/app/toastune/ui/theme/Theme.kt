package app.toastune.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.toastune.R
import app.toastune.data.ThemeMode

/** Outfit (OFL) con una fuente estática por peso: así el peso se respeta en todos los celulares. */
val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)

/** OpenDyslexic (OFL): letras con la base más pesada para que no "giren" ni se confundan. */
val OpenDyslexic = FontFamily(
    Font(R.font.opendyslexic_regular, FontWeight.Normal),
    Font(R.font.opendyslexic_bold, FontWeight.Bold),
)

private fun typography(family: FontFamily, dys: Boolean): Typography {
    fun t(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) = TextStyle(
        fontFamily = family,
        // En modo dislexia solo hay dos pesos y todo va con más aire entre letras y líneas.
        fontWeight = if (dys) (if (weight >= FontWeight.SemiBold) FontWeight.Bold else FontWeight.Normal) else weight,
        fontSize = (if (dys) size * 0.94f else size.toFloat()).sp,
        lineHeight = (if (dys) line * 1.3f else line.toFloat()).sp,
        letterSpacing = (if (dys) 0.5 else spacing).sp,
    )
    return Typography(
        displaySmall = t(38, FontWeight.Bold, 44, -0.5),
        headlineLarge = t(32, FontWeight.Bold, 38, -0.4),
        headlineMedium = t(28, FontWeight.Bold, 34, -0.3),
        headlineSmall = t(24, FontWeight.SemiBold, 30, -0.2),
        titleLarge = t(22, FontWeight.SemiBold, 28),
        titleMedium = t(17, FontWeight.SemiBold, 23),
        titleSmall = t(15, FontWeight.SemiBold, 20),
        bodyLarge = t(17, FontWeight.Normal, 24),
        bodyMedium = t(15, FontWeight.Normal, 21),
        bodySmall = t(13, FontWeight.Normal, 18),
        labelLarge = t(15, FontWeight.SemiBold, 20),
        labelMedium = t(13, FontWeight.Medium, 17),
        labelSmall = t(12, FontWeight.Medium, 16),
    )
}

private val NormalTypography = typography(Outfit, false)
private val DyslexicTypography = typography(OpenDyslexic, true)

/** Colores propios del cristal; no existen en Material. */
@Stable
class GlassColors(
    val base: Color,
    val tint: Color,
    val frost: Color,
    val highlight: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val scrim: Color,
    val dark: Boolean,
)

val LocalGlassColors = staticCompositionLocalOf<GlassColors> { error("sin tema") }
val LocalGlassEnabled = staticCompositionLocalOf { true }
val LocalCoverColors = staticCompositionLocalOf { true }

val Toast = Color(0xFF7FA8FF)
val Rose = Color(0xFFFF5C8A)

private val DarkColors = darkColorScheme(
    primary = Toast,
    onPrimary = Color(0xFF06173F),
    primaryContainer = Color(0xFF1C3470),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Rose,
    onSecondary = Color(0xFF4A0019),
    tertiary = Color(0xFF7FE3D6),
    background = Color(0xFF0A0810),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0A0810),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2A2234),
    onSurfaceVariant = Color(0xFFD2CADB),
    outline = Color(0xFF9D94A8),
    surfaceContainer = Color(0xFF181220),
    surfaceContainerHigh = Color(0xFF211A2A),
    surfaceContainerHighest = Color(0xFF2B2335),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5FD0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF06173F),
    secondary = Color(0xFFD6336C),
    onSecondary = Color.White,
    tertiary = Color(0xFF0E8F83),
    background = Color(0xFFF8F3F6),
    onBackground = Color(0xFF1E1823),
    surface = Color(0xFFF8F3F6),
    onSurface = Color(0xFF1E1823),
    surfaceVariant = Color(0xFFE9DFEC),
    onSurfaceVariant = Color(0xFF3F364A),
    outline = Color(0xFF80768A),
    surfaceContainer = Color(0xFFEFE6F1),
    surfaceContainerHigh = Color(0xFFE8DEEB),
    surfaceContainerHighest = Color(0xFFE1D6E5),
)

private val DarkGlass = GlassColors(
    base = Color(0xFF0A0810),
    tint = Color(0xFF14101C).copy(alpha = 0.80f),
    frost = Color(0xFF1E1828).copy(alpha = 0.92f),
    highlight = Color.White.copy(alpha = 0.05f),
    borderTop = Color.White.copy(alpha = 0.13f),
    borderBottom = Color.White.copy(alpha = 0.13f),
    scrim = Color.Black.copy(alpha = 0.55f),
    dark = true,
)

private val LightGlass = GlassColors(
    base = Color(0xFFF8F3F6),
    tint = Color(0xFFFFFFFF).copy(alpha = 0.82f),
    frost = Color.White.copy(alpha = 0.90f),
    highlight = Color.White.copy(alpha = 0.30f),
    borderTop = Color(0xFF3F364A).copy(alpha = 0.12f),
    borderBottom = Color(0xFF3F364A).copy(alpha = 0.12f),
    scrim = Color(0xFF1E1823).copy(alpha = 0.40f),
    dark = false,
)

@Composable
fun ToastuneTheme(
    mode: ThemeMode,
    glass: Boolean,
    coverColors: Boolean,
    dyslexic: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalGlassColors provides (if (dark) DarkGlass else LightGlass),
        LocalGlassEnabled provides glass,
        LocalCoverColors provides coverColors,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = if (dyslexic) DyslexicTypography else NormalTypography,
            content = content,
        )
    }
}
