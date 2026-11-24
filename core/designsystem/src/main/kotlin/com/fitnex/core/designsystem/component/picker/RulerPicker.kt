package com.fitnex.core.designsystem.component.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import kotlin.math.roundToInt

/**
 * Horizontal ruler to pick a number by sliding the finger: the big value on top, a tape of ticks
 * underneath (a long tick every [majorEvery] steps) and a fixed centre marker. It snaps to the
 * nearest step and gives a light tick of haptics per step.
 *
 * Accessibility: exposed as an adjustable value (TalkBack volume/swipe gestures change it).
 */
@Composable
fun FitnexRulerPicker(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    min: Float,
    max: Float,
    step: Float,
    unit: String,
    modifier: Modifier = Modifier,
    majorEvery: Int = 10,
    format: (Float) -> String = { if (it % 1f == 0f) it.toInt().toString() else it.toString() },
    testTag: String = "",
) {
    val colors = FitnexTheme.colors
    val count = ((max - min) / step).roundToInt() + 1
    fun indexOf(v: Float) = ((v.coerceIn(min, max) - min) / step).roundToInt().coerceIn(0, count - 1)
    fun valueAt(i: Int) = min + i * step

    val state = rememberLazyListState(initialFirstVisibleItemIndex = indexOf(value))
    val tickWidth = 12.dp
    val tickPx = with(LocalDensity.current) { tickWidth.toPx() }
    val centred by remember {
        derivedStateOf { (state.firstVisibleItemIndex + (state.firstVisibleItemScrollOffset / tickPx).roundToInt()).coerceIn(0, count - 1) }
    }
    val haptics = LocalHapticFeedback.current
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)

    // True while the tape moves by itself to follow an outside change: that motion must not edit the value.
    val programmatic = remember { androidx.compose.runtime.mutableStateOf(false) }
    // Finger → value: every step crossed while scrolling updates the value (with a light tick).
    LaunchedEffect(state, count) {
        snapshotFlow { centred }.collect { i ->
            if (state.isScrollInProgress && !programmatic.value && indexOf(latestValue) != i) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                latestOnChange(valueAt(i))
            }
        }
    }
    // When a fling settles, the value is wherever the tape stopped.
    LaunchedEffect(state, count) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && !programmatic.value && indexOf(latestValue) != centred) latestOnChange(valueAt(centred))
        }
    }
    // Value → ruler: outside changes (a new proposed set, the accessibility action) move the tape.
    LaunchedEffect(value) {
        val target = indexOf(value)
        if (!state.isScrollInProgress && centred != target) {
            programmatic.value = true
            try { state.animateScrollToItem(target) } finally { programmatic.value = false }
        }
    }

    val shown = valueAt(centred.takeIf { state.isScrollInProgress } ?: indexOf(value))
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = "${format(value)} $unit"
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(min, max), min..max, steps = (count - 2).coerceAtLeast(0))
                setProgress { target ->
                    latestOnChange(valueAt(indexOf(target)))
                    true
                }
            }
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label.uppercase(), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            Text(
                format(shown),
                style = FitnexTheme.typography.displayMedium.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            Text(" $unit", style = FitnexTheme.typography.titleMedium, color = colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp).padding(top = Spacing.xs), contentAlignment = Alignment.Center) {
            val side = (maxWidth - tickWidth) / 2
            LazyRow(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state),
                contentPadding = PaddingValues(horizontal = side),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    // Ticks fade out towards the edges.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.horizontalGradient(0f to Color.Transparent, 0.2f to Color.Black, 0.8f to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                items(count) { i ->
                    val major = i % majorEvery == 0
                    val half = !major && majorEvery % 2 == 0 && i % (majorEvery / 2) == 0
                    Box(
                        Modifier
                            .width(tickWidth)
                            .height(48.dp)
                            .drawBehind {
                                val h = size.height * when { major -> 0.62f; half -> 0.44f; else -> 0.3f }
                                val x = size.width / 2
                                drawLine(
                                    if (major) colors.textSecondary else colors.textDisabled,
                                    Offset(x, size.height - h), Offset(x, size.height),
                                    strokeWidth = (if (major) 2.dp else 1.5.dp).toPx(), cap = StrokeCap.Round,
                                )
                            },
                    )
                }
            }
            // Fixed centre marker in the accent.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(3.dp)
                    .height(40.dp)
                    .background(colors.accent, RoundedCornerShape(50)),
            )
        }
    }
}
