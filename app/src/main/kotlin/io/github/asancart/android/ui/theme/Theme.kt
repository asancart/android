package io.github.asancart.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** REQ-BRAND-005: light, dark, or follow the system (default). */
enum class ThemeChoice { LIGHT, DARK, SYSTEM }

/** Radius tokens: sm 7, md 14, lg 18, pill. */
@Immutable
object AcShapes {
    val sm = RoundedCornerShape(7.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(percent = 50)

    /** Leading icon tiles and stat tiles in the design. */
    val tile = RoundedCornerShape(12.dp)
}

/** Status tones (REQ-BRAND-004 keeps them apart from the brand colours). */
enum class Tone { NEUTRAL, PRIMARY, OK, WARN, BAD, INFO }

private val LocalAcColors = staticCompositionLocalOf { LightAcColors }
private val LocalAcType = staticCompositionLocalOf { AcType }

object AcTheme {
    val colors: AcColors
        @Composable @ReadOnlyComposable
        get() = LocalAcColors.current

    val type: AcTypography
        @Composable @ReadOnlyComposable
        get() = LocalAcType.current
}

/** Foreground of a tone: icons, dots, pill text. */
fun AcColors.toneColor(tone: Tone): Color = when (tone) {
    Tone.NEUTRAL -> muted
    Tone.PRIMARY -> primaryText
    Tone.OK -> ok
    Tone.WARN -> warn
    Tone.BAD -> bad
    Tone.INFO -> info
}

/** Tinted fill behind a tone, as `color-mix(tone N%, transparent)` in the design. */
fun AcColors.toneFill(tone: Tone): Color = when (tone) {
    Tone.NEUTRAL -> surface2
    Tone.PRIMARY -> primary.copy(alpha = 0.14f)
    Tone.OK -> ok.copy(alpha = 0.14f)
    Tone.WARN -> warn.copy(alpha = 0.15f)
    Tone.BAD -> bad.copy(alpha = 0.13f)
    Tone.INFO -> info.copy(alpha = 0.14f)
}

/** `color-mix(in srgb, color pct%, base)`. */
fun mix(base: Color, color: Color, fraction: Float): Color = lerp(base, color, fraction)

@Composable
fun AsancartTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkAcColors else LightAcColors
    CompositionLocalProvider(LocalAcColors provides colors, LocalAcType provides AcType) {
        MaterialTheme(colorScheme = colors.toColorScheme(), typography = materialTypography, shapes = materialShapes) {
            CompositionLocalProvider(
                LocalContentColor provides colors.ink,
                LocalTextStyle provides AcType.body,
                content = content,
            )
        }
    }
}

private val materialTypography = Typography(
    bodyLarge = AcType.body,
    bodyMedium = AcType.body,
    bodySmall = AcType.small,
    labelLarge = AcType.button,
    labelMedium = AcType.nav,
    labelSmall = AcType.caption,
    titleLarge = AcType.barTitle,
    titleMedium = AcType.title,
    titleSmall = AcType.section,
    headlineLarge = AcType.display,
    headlineMedium = AcType.screenTitle,
    headlineSmall = AcType.brand,
)

private val materialShapes = Shapes(
    extraSmall = AcShapes.sm,
    small = AcShapes.sm,
    medium = AcShapes.md,
    large = AcShapes.lg,
    extraLarge = RoundedCornerShape(26.dp),
)

/** Material components that aren't restyled still draw in brand tokens. */
private fun AcColors.toColorScheme(): ColorScheme = if (isDark) darkScheme(this) else lightScheme(this)

private fun lightScheme(c: AcColors) = lightColorScheme(
    primary = c.primary, onPrimary = c.onPrimary,
    primaryContainer = mix(c.surface, c.primary, 0.14f), onPrimaryContainer = c.primaryText,
    secondary = c.accent, onSecondary = c.onAccent,
    secondaryContainer = mix(c.surface, c.accent, 0.14f), onSecondaryContainer = c.ink,
    tertiary = c.info, onTertiary = c.surface,
    background = c.bg, onBackground = c.ink,
    surface = c.surface, onSurface = c.ink,
    surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
    surfaceTint = c.primary,
    inverseSurface = c.ink, inverseOnSurface = c.bg, inversePrimary = c.primary,
    error = c.bad, onError = c.onPrimary,
    outline = c.line, outlineVariant = c.line,
    scrim = c.overlay,
    surfaceBright = c.surface, surfaceDim = c.surface2,
    surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
    surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface2,
)

private fun darkScheme(c: AcColors) = darkColorScheme(
    primary = c.primary, onPrimary = c.onPrimary,
    primaryContainer = mix(c.surface, c.primary, 0.14f), onPrimaryContainer = c.primaryText,
    secondary = c.accent, onSecondary = c.onAccent,
    secondaryContainer = mix(c.surface, c.accent, 0.14f), onSecondaryContainer = c.ink,
    tertiary = c.info, onTertiary = c.surface,
    background = c.bg, onBackground = c.ink,
    surface = c.surface, onSurface = c.ink,
    surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
    surfaceTint = c.primary,
    inverseSurface = c.ink, inverseOnSurface = c.bg, inversePrimary = c.primary,
    error = c.bad, onError = c.onPrimary,
    outline = c.line, outlineVariant = c.line,
    scrim = c.overlay,
    surfaceBright = c.surface2, surfaceDim = c.bg,
    surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
    surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface2,
)
