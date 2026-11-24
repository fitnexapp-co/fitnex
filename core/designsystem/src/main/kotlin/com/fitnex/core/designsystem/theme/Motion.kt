package com.fitnex.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Motion rules shared by every component. With "Reducir animaciones" decorative motion is gone and
 * functional feedback becomes instant state or opacity changes.
 */
object FitnexMotion {
    /** Fast out, gentle settle: the house easing. */
    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val SHORT = 180
    const val MEDIUM = 280
    const val LONG = 600

    @Composable
    fun <T> spec(durationMs: Int = MEDIUM): FiniteAnimationSpec<T> =
        if (FitnexTheme.appearance.reduceMotion) snap() else tween(durationMs, easing = Easing)

    /** Springy only when motion is allowed; never bouncy (damping ≥ 0.8). */
    @Composable
    fun <T> pressSpring(): FiniteAnimationSpec<T> =
        if (FitnexTheme.appearance.reduceMotion) snap() else spring(dampingRatio = 0.85f, stiffness = 700f)
}

/**
 * Press feedback with spring physics: scale 0.965 (liquid-glass feel); with reduced motion only a
 * light dim. Applies to anything with an [InteractionSource] (buttons, cards, tiles).
 */
@Composable
fun Modifier.pressFeedback(interaction: InteractionSource, enabled: Boolean = true): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val reduce = FitnexTheme.appearance.reduceMotion
    val scale by animateFloatAsState(if (pressed && enabled && !reduce) 0.965f else 1f, FitnexMotion.pressSpring(), label = "press")
    val alpha = if (pressed && enabled && reduce) 0.7f else 1f
    return graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
}

/** Set once the first entrance of this app session has played. */
private object EntranceOnce { @Volatile var done = false }

/**
 * Entrance: fade in and rise [risePx] dp (10 by default). It plays only on the first screen of
 * the app session; coming back to a screen (tab switch, back) shows it instantly, so the entrance
 * never repeats. Nothing with reduced motion.
 */
@Composable
fun Modifier.enterAnimation(delayMs: Int = 0, risePx: Int = 10): Modifier {
    if (FitnexTheme.appearance.reduceMotion) return this
    val play = remember { !EntranceOnce.done }
    if (!play) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        try {
            progress.animateTo(1f, tween(FitnexMotion.MEDIUM + 60, delayMillis = delayMs, easing = FitnexMotion.Easing))
        } finally {
            EntranceOnce.done = true
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * risePx * density
    }
}
