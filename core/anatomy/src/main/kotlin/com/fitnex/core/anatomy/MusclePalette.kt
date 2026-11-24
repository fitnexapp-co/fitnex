package com.fitnex.core.anatomy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.fitnex.core.designsystem.theme.FitnexTheme

/**
 * Shared by the 2D and 3D maps: a white, statue-like human figure. Muscles are white separated by
 * fine grey lines; worked or selected muscles take the user's accent colour (stronger = more work).
 */
@Immutable
data class MusclePalette(
    /** Not-worked muscle surface. */
    val idle: Color,
    /** Accent for worked muscles. */
    val active: Color,
    val selected: Color,
    /** Skin between muscles (head, hands, joints): a hair darker than [idle]. */
    val body: Color,
    /** Lighter top of the body gradient (porcelain shading). */
    val bodyHighlight: Color,
    /** Thin lines between muscles. */
    val outline: Color,
) {
    /** [level] 0 = not worked; (0, 1] = share of the most-worked muscle. */
    fun fill(level: Float): Color =
        if (level <= 0f) idle else lerp(idle, active, MIN_ACCENT + (1f - MIN_ACCENT) * level.coerceIn(0f, 1f))

    private companion object {
        /** Even the least-worked muscle must read clearly as accent, not as a tinted white. */
        const val MIN_ACCENT = 0.4f
    }
}

@Composable
fun rememberMusclePalette(): MusclePalette {
    val c = FitnexTheme.colors
    return if (c.isDark) {
        MusclePalette(
            idle = Color(0xFFF6F6F9), active = c.accent, selected = c.accentStrong,
            body = Color(0xFFD6D7DE), bodyHighlight = Color(0xFFEDEEF2), outline = Color(0xFF9D9EA8),
        )
    } else {
        // On a white card the figure needs a touch more grey to keep its shape.
        MusclePalette(
            idle = Color(0xFFFBFBFD), active = c.accent, selected = c.accentStrong,
            body = Color(0xFFD9DAE1), bodyHighlight = Color(0xFFEBECF1), outline = Color(0xFFA4A5AE),
        )
    }
}

/**
 * 3D map: a uniform grey sculpture (like a stone or clay statue). Muscles and skin share the same
 * grey so the model's own anatomy carries the shape; only worked muscles light up in the accent.
 */
@Composable
fun rememberStatuePalette(): MusclePalette {
    val c = FitnexTheme.colors
    val grey = if (c.isDark) Color(0xFFB4B6BE) else Color(0xFFA9ABB4)
    return MusclePalette(
        idle = grey, active = c.accent, selected = lerp(c.accent, Color.White, 0.25f),
        body = grey, bodyHighlight = lerp(grey, Color.White, 0.25f), outline = lerp(grey, Color.Black, 0.05f),
    )
}
