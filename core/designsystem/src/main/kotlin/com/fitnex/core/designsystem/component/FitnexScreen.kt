package com.fitnex.core.designsystem.component

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fitnex.core.designsystem.theme.FitnexTheme

/** The app backdrop (black, faint accent light) as a modifier, shared by the root and every page. */
@Composable
fun Modifier.fitnexBackdrop(): Modifier {
    val colors = FitnexTheme.colors
    val liquid = FitnexTheme.appearance.liquidGlass
    return this
        .background(colors.background)
        .drawBehind {
            val glowAlpha = 0.07f * (if (liquid) 1.8f else 1f)
            val r = size.maxDimension * 0.6f
            val c = Offset(size.width * 0.85f, -size.height * 0.05f)
            drawCircle(Brush.radialGradient(listOf(colors.accent.copy(alpha = glowAlpha), Color.Transparent), c, r), r, c)
            if (liquid) {
                val r2 = size.maxDimension * 0.45f
                val c2 = Offset(0f, size.height * 0.75f)
                drawCircle(Brush.radialGradient(listOf(colors.accentStrong.copy(alpha = glowAlpha * 0.6f), Color.Transparent), c2, r2), r2, c2)
            }
        }
}

/**
 * A navigation destination drawn on its own opaque backdrop, like a real page: while one screen
 * slides over another (push, back, the back gesture) they never show through each other.
 */
inline fun <reified T : Any> NavGraphBuilder.fitnexScreen(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        val scope = this
        Box(Modifier.fillMaxSize().fitnexBackdrop()) { scope.content(entry) }
    }
}
