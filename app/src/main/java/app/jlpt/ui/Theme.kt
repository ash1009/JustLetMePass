package app.jlpt.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Extra colours the Material scheme has no name for. */
data class Extra(
    val good: Color,
    val goodSoft: Color,
    val bad: Color,
    val badSoft: Color,
    val ruby: Color,
    val card: Color,
    val line: Color,
    val muted: Color,
)

val LocalExtra = staticCompositionLocalOf {
    Extra(Color.Green, Color.Green, Color.Red, Color.Red, Color.Gray, Color.White, Color.LightGray, Color.Gray)
}

private val Ink = Color(0xFF26242C)
private val Paper = Color(0xFFFFF8EE)
private val Hanko = Color(0xFFD6453D)
private val Indigo = Color(0xFF3D5A80)

private val light = lightColorScheme(
    primary = Hanko,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE0DA),
    onPrimaryContainer = Color(0xFF5C1712),
    secondary = Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE6F3),
    onSecondaryContainer = Color(0xFF1B2A3F),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF3E8D6),
    onSurfaceVariant = Color(0xFF5E5850),
    surfaceContainer = Color(0xFFFAF0E1),
    surfaceContainerHigh = Color(0xFFF5E9D6),
    outline = Color(0xFFCBBFAE),
    outlineVariant = Color(0xFFE6DACA),
)

private val dark = darkColorScheme(
    primary = Color(0xFFFF8A7E),
    onPrimary = Color(0xFF4A0F0A),
    primaryContainer = Color(0xFF6E2A23),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFF9DB8DD),
    onSecondary = Color(0xFF14243A),
    secondaryContainer = Color(0xFF2B3E58),
    onSecondaryContainer = Color(0xFFDDE6F3),
    background = Color(0xFF1A1820),
    onBackground = Color(0xFFEDE6DC),
    surface = Color(0xFF1A1820),
    onSurface = Color(0xFFEDE6DC),
    surfaceVariant = Color(0xFF2C2933),
    onSurfaceVariant = Color(0xFFC4BCB0),
    surfaceContainer = Color(0xFF221F28),
    surfaceContainerHigh = Color(0xFF2A2730),
    outline = Color(0xFF5A5560),
    outlineVariant = Color(0xFF3A3640),
)

private val extraLight = Extra(
    good = Color(0xFF2E7D4F), goodSoft = Color(0xFFDDF1E3),
    bad = Color(0xFFC0392B), badSoft = Color(0xFFFADBD7),
    ruby = Color(0xFF8A7F72), card = Color(0xFFFFFDF8), line = Color(0xFFE6DACA), muted = Color(0xFF8A8278),
)
private val extraDark = Extra(
    good = Color(0xFF7FD69F), goodSoft = Color(0xFF203A2A),
    bad = Color(0xFFFF8A7E), badSoft = Color(0xFF462420),
    ruby = Color(0xFFA9A094), card = Color(0xFF24212B), line = Color(0xFF3A3640), muted = Color(0xFF9A9288),
)

/** Big Japanese characters look best in the brush-like Mincho style the phone has. */
val JpBig = FontFamily.Serif

private val type = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val SmallCaps = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)

@Composable
fun JlptTheme(mode: String, content: @Composable () -> Unit) {
    val isDark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides if (isDark) extraDark else extraLight) {
        val scheme = if (isDark) dark else light
        MaterialTheme(colorScheme = scheme, typography = type) {
            // Text with no colour of its own follows the theme (light text in dark mode).
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides scheme.onBackground,
                content = content,
            )
        }
    }
}
