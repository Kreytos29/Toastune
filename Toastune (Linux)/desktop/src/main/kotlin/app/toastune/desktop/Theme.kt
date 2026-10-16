package app.toastune.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

object Palette {
    val blue = Color(0xFF7FA8FF)
    val rose = Color(0xFFFF5C8A)
    val bg = Color(0xFF0A0810)
    val surface = Color(0xFF181220)
    val surfaceHigh = Color(0xFF2B2335)
    val muted = Color(0xFFD2CADB)
}

val Outfit = FontFamily(
    Font("fonts/outfit_regular.ttf", FontWeight.Normal),
    Font("fonts/outfit_medium.ttf", FontWeight.Medium),
    Font("fonts/outfit_semibold.ttf", FontWeight.SemiBold),
    Font("fonts/outfit_bold.ttf", FontWeight.Bold),
)

/** OpenDyslexic (OFL): letras con la base más pesada para que no "giren" ni se confundan. */
val OpenDyslexic = FontFamily(
    Font("fonts/opendyslexic_regular.otf", FontWeight.Normal),
    Font("fonts/opendyslexic_bold.otf", FontWeight.Bold),
)

private fun typography(family: FontFamily, dyslexic: Boolean): Typography {
    val b = Typography()
    fun TextStyle.f() = copy(
        fontFamily = family,
        letterSpacing = if (dyslexic) 0.6.sp else letterSpacing,
        lineHeight = if (dyslexic && lineHeight.isSpecified) lineHeight * 1.25f else lineHeight,
    )
    return b.copy(
        displayLarge = b.displayLarge.f(), displayMedium = b.displayMedium.f(), displaySmall = b.displaySmall.f(),
        headlineLarge = b.headlineLarge.f(), headlineMedium = b.headlineMedium.f(), headlineSmall = b.headlineSmall.f(),
        titleLarge = b.titleLarge.f(), titleMedium = b.titleMedium.f(), titleSmall = b.titleSmall.f(),
        bodyLarge = b.bodyLarge.f(), bodyMedium = b.bodyMedium.f(), bodySmall = b.bodySmall.f(),
        labelLarge = b.labelLarge.f(), labelMedium = b.labelMedium.f(), labelSmall = b.labelSmall.f(),
    )
}

private val NormalTypography by lazy { typography(Outfit, false) }
private val DyslexicTypography by lazy { typography(OpenDyslexic, true) }

@Composable
fun ToastuneTheme(dyslexic: Boolean = false, content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Palette.blue, onPrimary = Color(0xFF06173F),
        primaryContainer = Color(0xFF1C3470), onPrimaryContainer = Color(0xFFDCE6FF),
        secondary = Palette.rose, onSecondary = Color(0xFF4A0019),
        background = Palette.bg, onBackground = Color.White,
        surface = Palette.bg, onSurface = Color.White,
        surfaceVariant = Palette.surfaceHigh, onSurfaceVariant = Palette.muted,
        surfaceContainer = Palette.surface, surfaceContainerHigh = Color(0xFF211A2A),
        surfaceContainerHighest = Palette.surfaceHigh,
    ),
    typography = if (dyslexic) DyslexicTypography else NormalTypography,
    content = content,
)
