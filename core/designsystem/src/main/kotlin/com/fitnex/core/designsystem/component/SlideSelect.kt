package com.fitnex.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.fitnex.core.designsystem.theme.FitnexTheme

/**
 * iPhone-style selectors: the pill follows the finger while it slides across the options, every
 * option crossed gives a light tick, and lifting the finger selects. Taps keep working as usual.
 */
@Stable
class SlideSelectState internal constructor() {
    /** Finger position along the selector, px; null when not dragging. */
    var dragX by mutableStateOf<Float?>(null)
        internal set
    internal var width by mutableStateOf(1f)
    internal var count by mutableStateOf(1)

    val dragging: Boolean get() = dragX != null


    /** Option under the finger while dragging. */
    val hoverIndex: Int?
        get() = dragX?.let { indexAt(it) }

    internal fun indexAt(x: Float): Int =
        (x / (width / count.coerceAtLeast(1))).toInt().coerceIn(0, count - 1)
}

@Composable
fun rememberSlideSelectState(): SlideSelectState = remember { SlideSelectState() }

/** Drag handling for a selector of [count] equal-width options. */
@Composable
fun Modifier.slideToSelect(state: SlideSelectState, count: Int, onSelect: (Int) -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    state.count = count
    return this.pointerInput(count) {
        var last = -1
        detectHorizontalDragGestures(
            onDragStart = { o ->
                state.width = size.width.toFloat()
                state.dragX = o.x
                last = state.indexAt(o.x)
            },
            onDragEnd = {
                state.dragX?.let { onSelect(state.indexAt(it)) }
                state.dragX = null
            },
            onDragCancel = { state.dragX = null },
        ) { change, _ ->
            change.consume()
            val x = change.position.x.coerceIn(0f, size.width.toFloat())
            state.dragX = x
            val i = state.indexAt(x)
            if (i != last) {
                last = i
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

/**
 * Pill position in "option units" (0 = first option): glued to the finger while dragging, then a
 * soft spring to the selected option. Reduced motion: no spring, it just moves.
 */
@Composable
fun animatedPillPosition(selectedIndex: Int, state: SlideSelectState): State<Float> {
    val reduce = FitnexTheme.appearance.reduceMotion
    val target = state.dragX?.let { x ->
        val seg = state.width / state.count.coerceAtLeast(1)
        (x / seg - 0.5f).coerceIn(0f, (state.count - 1).toFloat())
    } ?: selectedIndex.coerceAtLeast(0).toFloat()
    return animateFloatAsState(
        target,
        when {
            reduce -> spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy)
            state.dragging -> spring(stiffness = 2200f, dampingRatio = 0.9f)
            // Firm and without wobble: switching tabs happens many times a day.
            else -> spring(stiffness = 600f, dampingRatio = 0.9f)
        },
        label = "pill",
    )
}
