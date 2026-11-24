package com.fitnex.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Raw palette. Screens never use these directly: they read [FitnexColors] via FitnexTheme.colors. */
internal object Palette {
    // Dark: deep black base, graphite surfaces separated by small luminance steps.
    // Pure black like OLED system apps; cards are a soft graphite that floats on it.
    val Night = Color(0xFF000000)
    val Graphite900 = Color(0xFF19191C)
    val Graphite800 = Color(0xFF242428)
    val Graphite700 = Color(0xFF303035)
    val Fog = Color(0xFFF5F5F7)
    val Grey400 = Color(0xFF9A9AA3)
    val Grey600 = Color(0xFF5C5C64)

    // Light: soft grouped grey with white cards.
    val Mist = Color(0xFFF2F2F6)
    val Ink = Color(0xFF111114)
    val InkSecondary = Color(0xFF6B6B73)
    val InkDisabled = Color(0xFFA9A9B0)
}

/** The accent presets offered in Settings. [Violet] is the FITNEX default. */
object FitnexAccents {
    val Violet = Color(0xFF8B5CF6)
    val Blue = Color(0xFF3B82F6)
    val Cyan = Color(0xFF38BDF8)
    val Coral = Color(0xFFFF6B6B)
    val Orange = Color(0xFFF59E0B)
    val Pink = Color(0xFFEC4899)
    val Red = Color(0xFFEF4444)
    val Green = Color(0xFF22C55E)

    val Default: Color = Violet
    val presets: List<Color> = listOf(Violet, Blue, Cyan, Coral, Orange, Pink, Red, Green)
}

/**
 * The FITNEX signature look (the default accent): violet controls over a black backdrop lit by
 * electric-blue, mint-green and violet light. With any other accent, that one colour lights everything.
 */
object FitnexSignature {
    val Blue = Color(0xFF2F6BFF)
    val Mint = Color(0xFF34D399)
    val Violet = FitnexAccents.Violet
}

@Immutable
data class FitnexColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    /** Fill of glass cards (on top of [surface]). */
    val glassFill: Color,
    val glassHighlight: Color,
    val glassBorder: Color,
    val accent: Color,
    val accentStrong: Color,
    /** Accent at low strength over the surface: tinted backgrounds, inactive bars, ring tracks. */
    val accentMuted: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val divider: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    /** Muscle map code (spec §5): light = idle, accent = selected/trained, grey = secondary. */
    val muscleIdle: Color,
    val muscleActive: Color,
    val muscleSecondary: Color,
    val isDark: Boolean,
    /** Default FITNEX style (multicolour light, neutral selections) vs. a user-picked accent. */
    val signature: Boolean = false,
    /** The three lights painted on the backdrop: top streak, side arc, bottom glow. */
    val glowTop: Color = accent,
    val glowSide: Color = accent,
    val glowBottom: Color = accent,
)

/**
 * Builds the whole palette around one accent. Only accent-derived roles change with the user's
 * choice; background, surfaces and text never do (spec: the accent is the only variable).
 */
fun fitnexColors(dark: Boolean, accent: Color, signature: Boolean = accent == FitnexAccents.Default): FitnexColors =
    baseColors(dark, accent).let { c ->
        if (signature) {
            c.copy(signature = true, glowTop = FitnexSignature.Blue, glowSide = FitnexSignature.Mint, glowBottom = FitnexSignature.Violet)
        } else {
            c.copy(signature = false, glowTop = c.accent, glowSide = c.accentStrong, glowBottom = c.accent)
        }
    }

private fun baseColors(dark: Boolean, accent: Color): FitnexColors {
    val a = if (dark) accent.copy(alpha = 1f) else accent.copy(alpha = 1f).withContrastOn(Color.White, minRatio = 3.0)
    // Whichever reads better on this accent (white on violet, ink on cyan or pastel picks).
    val onAccent = if (contrastRatio(Palette.Ink, a) > contrastRatio(Color.White, a) * 1.25) Palette.Ink else Color.White
    return if (dark) {
        FitnexColors(
            background = Palette.Night,
            surface = Palette.Graphite900,
            surfaceRaised = Palette.Graphite800,
            glassFill = Color.White.copy(alpha = 0.035f),
            glassHighlight = Color.White.copy(alpha = 0.06f),
            glassBorder = Color.White.copy(alpha = 0.08f),
            accent = a,
            accentStrong = lerp(a, Color.White, 0.22f),
            accentMuted = a.copy(alpha = 0.22f).compositeOver(Palette.Graphite900),
            onAccent = onAccent,
            textPrimary = Palette.Fog,
            textSecondary = Palette.Grey400,
            textDisabled = Palette.Grey600,
            divider = Color.White.copy(alpha = 0.07f),
            success = Color(0xFF32D74B),
            warning = Color(0xFFFFB340),
            error = Color(0xFFFF5A52),
            muscleIdle = Color(0xFFE9E9EE),
            muscleActive = a,
            muscleSecondary = Palette.Grey600,
            isDark = true,
        )
    } else {
        FitnexColors(
            background = Palette.Mist,
            surface = Color.White,
            surfaceRaised = Color.White,
            glassFill = Color.White.copy(alpha = 0.72f),
            glassHighlight = Color.White.copy(alpha = 0.92f),
            glassBorder = Palette.Ink.copy(alpha = 0.07f),
            accent = a,
            accentStrong = lerp(a, Color.Black, 0.15f),
            accentMuted = a.copy(alpha = 0.16f).compositeOver(Color.White),
            onAccent = onAccent,
            textPrimary = Palette.Ink,
            textSecondary = Palette.InkSecondary,
            textDisabled = Palette.InkDisabled,
            divider = Palette.Ink.copy(alpha = 0.07f),
            success = Color(0xFF1F8A3B),
            warning = Color(0xFFB25E00),
            error = Color(0xFFD70F2C),
            muscleIdle = Color(0xFFE4E4EA),
            muscleActive = a,
            muscleSecondary = Color(0xFFB4B4BC),
            isDark = false,
        )
    }
}

/** WCAG contrast ratio between two opaque colours. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance() + 0.05
    val lb = b.luminance() + 0.05
    return if (la > lb) la / lb else lb / la
}

/** Darkens (on light backgrounds) until the colour reaches [minRatio] against [background]. */
internal fun Color.withContrastOn(background: Color, minRatio: Double): Color {
    var c = this
    var step = 0
    while (contrastRatio(c, background) < minRatio && step < 20) {
        c = lerp(c, Color.Black, 0.06f)
        step++
    }
    return c
}

@Immutable
object Spacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
    /** Horizontal screen gutter. */
    val gutter: Dp = 20.dp
}

@Immutable
object Radii {
    val sm: Dp = 10.dp
    val md: Dp = 14.dp
    val lg: Dp = 22.dp
    val xl: Dp = 30.dp
    val pill: Dp = 999.dp
}

@Immutable
object Elevation {
    val none: Dp = 0.dp
    val card: Dp = 2.dp
    val floating: Dp = 12.dp
}

internal val LocalFitnexColors = staticCompositionLocalOf { fitnexColors(dark = true, accent = FitnexAccents.Default) }
