package com.fitnex.core.anatomy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitnex.core.anatomy.map2d.BodyAtlas2D
import com.fitnex.core.anatomy.map2d.BodyView
import com.fitnex.core.anatomy.map2d.MuscleMap2D
import com.fitnex.core.anatomy.map3d.MuscleMap3D
import com.fitnex.core.anatomy.map3d.rememberMap3DState
import com.fitnex.core.anatomy.map3d.rememberSupports3D
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexSegmentedControl
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.Muscle

enum class MapMode { TWO_D, THREE_D }

/** The side of the body where [levels] add up to more work; the front on a tie or with no data. */
internal fun bestView(levels: Map<String, Float>): BodyView {
    fun work(view: BodyView) = BodyAtlas2D.muscleIds(view).sumOf { (levels[it] ?: 0f).toDouble() }
    return if (work(BodyView.BACK) > work(BodyView.FRONT)) BodyView.BACK else BodyView.FRONT
}

/**
 * The muscle map as a card: 2D/3D switch, front/back, the map, and contextual information for
 * the selected muscle (spec §5). Both maps share [levels] and the selection.
 *
 * @param levels heat 0..1 per muscle id; absent = not worked (white).
 * @param info extra line for a selected muscle (e.g. its sets in the period), or null.
 * @param onOpenMuscle opens the muscle's detail page; null hides the button.
 */
@Composable
fun MuscleMapCard(
    levels: Map<String, Float>,
    muscles: Map<String, Muscle>,
    info: @Composable (muscleId: String) -> String?,
    onOpenMuscle: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.map_title),
) {
    val colors = FitnexTheme.colors
    var failed3D by rememberSaveable { mutableStateOf(false) }
    val supports3D = rememberSupports3D() && !failed3D
    var mode by rememberSaveable { mutableStateOf(MapMode.TWO_D) }
    var view by rememberSaveable { mutableStateOf(bestView(levels)) }
    // Until the user picks a side, the map shows the one with more work (a face pull opens on the
    // back, where its main muscles are). Levels may arrive after the first frame.
    var viewChosen by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(levels) { if (!viewChosen) view = bestView(levels) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val map3D = rememberMap3DState()
    val shownMode = if (supports3D) mode else MapMode.TWO_D

    val worked = levels.filterValues { it > 0f }.keys.mapNotNull { muscles[it]?.name }
    val workedText = worked.joinToString().ifEmpty { stringResource(R.string.map_a11y_none) }
    val labels2D = mapOf(MapMode.TWO_D to stringResource(R.string.map_2d), MapMode.THREE_D to stringResource(R.string.map_3d))
    val labelsView = mapOf(BodyView.FRONT to stringResource(R.string.view_front), BodyView.BACK to stringResource(R.string.view_back))

    val reduce = FitnexTheme.appearance.reduceMotion
    val fade = { if (reduce) fadeIn(androidx.compose.animation.core.snap()) togetherWith fadeOut(androidx.compose.animation.core.snap()) else fadeIn(androidx.compose.animation.core.tween(240)) togetherWith fadeOut(androidx.compose.animation.core.tween(160)) }
    GlassCard(modifier.fillMaxWidth().testTag("muscle_map_card"), emphasis = com.fitnex.core.designsystem.component.CardEmphasis.Featured) {
        Text(title, style = FitnexTheme.typography.titleLarge, color = colors.textPrimary)
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (supports3D) {
                FitnexSegmentedControl(MapMode.entries, shownMode, { mode = it }, { labels2D.getValue(it) }, Modifier.weight(1f).testTag("map_mode"), compact = true)
            }
            FitnexSegmentedControl(BodyView.entries, view, { view = it; viewChosen = true }, { labelsView.getValue(it) }, Modifier.weight(1f).testTag("map_view"), compact = true)
        }
        Spacer(Modifier.height(Spacing.md))

        AnimatedContent(shownMode, transitionSpec = { fade() }, label = "mapMode") { m ->
            when (m) {
                MapMode.TWO_D -> AnimatedContent(view, transitionSpec = { fade() }, label = "mapView") { v ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MuscleMap2D(
                            view = v,
                            levels = levels,
                            selected = selected,
                            onSelect = { selected = it },
                            contentDescription = stringResource(R.string.map_a11y, labelsView.getValue(v), workedText),
                            modifier = Modifier.height(MAP_HEIGHT).testTag("muscle_map_2d"),
                        )
                    }
                }
                MapMode.THREE_D -> Column {
                    MuscleMap3D(
                        state = map3D,
                        view = view,
                        levels = levels,
                        selected = selected,
                        onSelect = { selected = it },
                        onUnavailable = { failed3D = true },
                        contentDescription = stringResource(R.string.map3d_a11y, workedText),
                        modifier = Modifier.fillMaxWidth().height(MAP_HEIGHT).testTag("muscle_map_3d"),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.hint_3d), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled, modifier = Modifier.weight(1f))
                        TextButton(onClick = { map3D.resetFraming() }) {
                            Text(stringResource(R.string.recenter), color = colors.accent)
                        }
                    }
                }
            }
        }
        if (!supports3D) {
            Text(stringResource(R.string.fallback_2d), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
        }

        // Contextual information for the selected muscle.
        val muscle = selected?.let { muscles[it] }
        if (muscle == null) {
            Text(stringResource(R.string.hint_tap), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.sm))
        } else {
            Column(Modifier.padding(top = Spacing.sm).testTag("muscle_info")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(muscle.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selected = null }) { Text(stringResource(R.string.clear_selection), color = colors.textSecondary) }
                }
                Text(muscle.function, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
                info(muscle.id)?.let { Text(it, style = FitnexTheme.typography.bodyMedium, color = colors.accent, modifier = Modifier.padding(top = Spacing.xs)) }
                if (onOpenMuscle != null) {
                    FitnexSecondaryButton(stringResource(R.string.open_detail), { onOpenMuscle(muscle.id) }, Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("muscle_open_detail"))
                }
            }
        }
        MapLegend(Modifier.padding(top = Spacing.md))
    }

    // A muscle picked in 3D stays visible in 2D: flip to the side that shows it.
    LaunchedEffect(selected, shownMode) {
        val id = selected ?: return@LaunchedEffect
        if (shownMode == MapMode.TWO_D && id !in BodyAtlas2D.muscleIds(view)) view = BodyAtlas2D.preferredView(id)
    }
}

@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    val palette = rememberMusclePalette()
    val colors = FitnexTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        listOf(
            // Two entries only: "Otras zonas" had almost the same white as "Sin trabajar" and read
            // as a duplicate.
            palette.idle to stringResource(R.string.legend_idle),
            palette.active to stringResource(R.string.legend_active),
        ).forEach { (c, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Dot(c)
                Text(label, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

/** Tall enough to show the whole figure, head to feet, as the centrepiece of Progreso. */
private val MAP_HEIGHT = 440.dp
