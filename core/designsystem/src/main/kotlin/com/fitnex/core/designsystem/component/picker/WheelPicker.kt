package com.fitnex.core.designsystem.component.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Vertical "drum" picker: swipe up/down and the item that stops in the centre band is selected.
 * Snaps to items, and every item is also tappable (and focusable for TalkBack), so it works
 * without precise swiping. Reports the centred index as it changes.
 */
@Composable
fun <T> FitnexWheelPicker(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    itemHeight: Dp = 44.dp,
    testTag: String? = null,
) {
    require(visibleCount % 2 == 1) { "visibleCount must be odd so one item sits in the centre" }
    val colors = FitnexTheme.colors
    val safeIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val state = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val scope = rememberCoroutineScope()
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val half = visibleCount / 2
    val onChange by rememberUpdatedState(onSelectedIndexChange)

    // With top/bottom padding of `half` items, the centred item is the first visible one.
    val centred by remember(items.size) {
        derivedStateOf {
            val i = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            i.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(centred) { if (centred != selectedIndex) onChange(centred) }
    // External change (e.g. day clamped when switching to February): move the wheel to it.
    LaunchedEffect(safeIndex) { if (!state.isScrollInProgress && safeIndex != centred) state.scrollToItem(safeIndex) }

    Box(modifier.height(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        // Selection band behind the centre item.
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .padding(horizontal = 4.dp)
                .background(colors.accent.copy(alpha = 0.16f), RoundedCornerShape(Radii.sm)),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = itemHeight * half),
            modifier = Modifier.fillMaxWidth().let { if (testTag != null) it.testTag(testTag) else it },
        ) {
            itemsIndexed(items) { index, item ->
                val distance = abs(index - centred)
                val isCentre = distance == 0
                Text(
                    text = label(item),
                    style = if (isCentre) FitnexTheme.typography.titleLarge else FitnexTheme.typography.titleMedium,
                    color = if (isCentre) colors.textPrimary else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            alpha = (1f - distance * 0.28f).coerceAtLeast(0.15f)
                            val s = (1f - distance * 0.06f).coerceAtLeast(0.8f)
                            scaleX = s; scaleY = s
                        }
                        .semantics { selected = isCentre }
                        .clickable(role = Role.Button) { scope.launch { state.animateScrollToItem(index) } }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
        // Fade the edges so the wheel reads as a drum.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to colors.surfaceRaised, 0.28f to Color.Transparent,
                    0.72f to Color.Transparent, 1f to colors.surfaceRaised,
                ),
            ),
        )
    }
}
