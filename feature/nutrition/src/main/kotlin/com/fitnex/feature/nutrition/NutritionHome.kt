package com.fitnex.feature.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnex.core.designsystem.component.CardEmphasis
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.FitnexMotion
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.ActivityLevel
import com.fitnex.core.model.CalculatorSex
import com.fitnex.core.model.CalorieAim
import com.fitnex.core.model.CalorieMath
import com.fitnex.feature.nutrition.ui.fmt
import java.time.LocalDate
import java.util.Locale

/** Macro colours, fixed so each nutrient keeps its colour across the app. */
internal object MacroColors {
    val Protein = Color(0xFF38BDF8)
    val Carbs = Color(0xFFF59E0B)
    val Fat = Color(0xFFC084FC)
}

/** Ring with a soft track; [progress] is clamped to 0..1. */
@Composable
internal fun MetricRing(
    progress: Float,
    color: Color,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    val colors = FitnexTheme.colors
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), FitnexMotion.spec(500), label = "metricRing")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = Stroke(stroke.toPx(), cap = StrokeCap.Round)
            drawArc(colors.textDisabled.copy(alpha = 0.35f), -90f, 360f, useCenter = false, style = s)
            if (animated > 0f) {
                drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.75f), color, color.copy(alpha = 0.75f))), -90f, 360f * animated, useCenter = false, style = s)
            }
        }
        center()
    }
}

/**
 * The day at a glance: calories against the adult's target (or the variety of foods for minors,
 * who never see calories) and three macro rings.
 */
@Composable
internal fun CaloriesCard(state: DiaryUiState, locale: Locale, onSetGoal: () -> Unit) {
    val colors = FitnexTheme.colors
    val day = state.day!!
    val t = day.totals
    val goal = state.kcalGoal
    val targets = state.macroTargets
    GlassCard(Modifier.fillMaxWidth().testTag("nutrition_summary"), emphasis = CardEmphasis.Featured, contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.audience.showKcal) {
                val kcalLabel = stringResource(R.string.kcal_ring_cd, fmt(t.kcal, locale))
                MetricRing(
                    progress = if (goal != null && goal > 0) t.kcal / goal else 0f, color = colors.accent, size = 132.dp, stroke = 12.dp,
                    modifier = Modifier.testTag("total_kcal").clearAndSetSemantics { contentDescription = kcalLabel }, // read once, not label + figures
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(FitnexIcons.Flame, null, tint = MacroColors.Carbs, modifier = Modifier.size(26.dp))
                        Text(fmt(t.kcal, locale), style = FitnexTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp), color = colors.textPrimary, maxLines = 1)
                        Text(stringResource(R.string.kcal_unit), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                }
                Column(Modifier.weight(1f).padding(start = Spacing.lg)) {
                    Text(stringResource(R.string.kcal_day), style = FitnexTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    if (goal != null) {
                        Text(stringResource(R.string.kcal_goal, "%,d".format(locale, goal)), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.testTag("kcal_goal"))
                        LinearBar(t.kcal / goal, colors.accent, Modifier.padding(vertical = Spacing.sm))
                        val left = goal - t.kcal
                        Text(
                            if (left >= 0f) stringResource(R.string.kcal_remaining, fmt(left, locale)) else stringResource(R.string.kcal_over, fmt(-left, locale)),
                            style = FitnexTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (left >= 0f) colors.accent else colors.textSecondary, // never an alarm for eating
                            modifier = Modifier.testTag("kcal_remaining"),
                        )
                    } else {
                        Text(stringResource(R.string.kcal_no_goal), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                        TextButton(onClick = onSetGoal, contentPadding = PaddingValues(0.dp), modifier = Modifier.testTag("kcal_set_goal")) {
                            Text(stringResource(R.string.kcal_set_goal), color = colors.accent, style = FitnexTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                // Minors: variety instead of calories (spec §4).
                MetricRing(progress = (day.distinctFoods / 10f), color = colors.accent, size = 120.dp, stroke = 11.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${day.distinctFoods}", style = FitnexTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = colors.textPrimary)
                        Text(stringResource(R.string.variety_short), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, textAlign = TextAlign.Center)
                    }
                }
                Column(Modifier.weight(1f).padding(start = Spacing.lg)) {
                    Text(stringResource(R.string.variety_title), style = FitnexTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    Text(stringResource(R.string.minor_focus), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs).testTag("minor_focus"))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.lg), horizontalArrangement = Arrangement.SpaceEvenly) {
            MacroRing(stringResource(R.string.protein), t.proteinG, targets?.proteinG, MacroColors.Protein, locale, "total_protein")
            MacroRing(stringResource(R.string.carbs_long), t.carbsG, targets?.carbsG, MacroColors.Carbs, locale, "total_carbs")
            MacroRing(stringResource(R.string.fat), t.fatG, targets?.fatG, MacroColors.Fat, locale, "total_fat")
        }
        Text(
            // Minors already see the variety in the big ring: only the fibre here.
            stringResource(R.string.fiber) + " ${fmt(t.fiberG, locale)} g" +
                if (state.audience.showKcal) " · " + androidx.compose.ui.res.pluralStringResource(R.plurals.variety, day.distinctFoods, day.distinctFoods) else "",
            style = FitnexTheme.typography.bodySmall, color = colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.md).testTag("total_fiber"),
        )
    }
}

@Composable
private fun MacroRing(label: String, grams: Float, target: Int?, color: Color, locale: Locale, tag: String) {
    val colors = FitnexTheme.colors
    val description = if (target != null) stringResource(R.string.macro_cd_target, label, fmt(grams, locale), target) else "$label, ${fmt(grams, locale)} g"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // One sentence for TalkBack ("Carbohidratos, 27 g de 81 g") instead of label + figures again.
        modifier = Modifier.testTag(tag).clearAndSetSemantics { text = androidx.compose.ui.text.AnnotatedString(description) },
    ) {
        MetricRing(progress = if (target != null && target > 0) grams / target else if (grams > 0f) 1f else 0f, color = if (target != null) color else color.copy(alpha = 0.55f), size = 92.dp, stroke = 6.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${fmt(grams, locale)} g", style = FitnexTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1)
                if (target != null) Text("/ $target g", style = FitnexTheme.typography.labelSmall, color = colors.textSecondary)
            }
        }
        Text(label, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs), maxLines = 1)
    }
}

@Composable
internal fun LinearBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val colors = FitnexTheme.colors
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), FitnexMotion.spec(FitnexMotion.LONG), label = "bar")
    Box(modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(colors.textDisabled.copy(alpha = 0.35f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.7f)))))
    }
}

/** One general, positive tip a day (same tip all day, another one tomorrow). */
@Composable
internal fun TipCard(date: LocalDate) {
    val colors = FitnexTheme.colors
    val tips = stringArrayResource(R.array.nutrition_tips)
    val tip = tips[Math.floorMod(date.toEpochDay(), tips.size.toLong()).toInt()]
    GlassCard(Modifier.fillMaxWidth().testTag("tip_card"), contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(FitnexIcons.Bulb, null, tint = colors.accent, modifier = Modifier.size(24.dp))
            Column(Modifier.padding(start = Spacing.md)) {
                Text(stringResource(R.string.tip_title), style = FitnexTheme.typography.labelLarge, color = colors.textSecondary)
                Text(tip, style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.padding(top = Spacing.xxs))
            }
        }
    }
}
internal data class NutritionTool(val icon: ImageVector, val label: String, val tag: String, val onClick: () -> Unit)

@Composable
internal fun ToolsGrid(tools: List<NutritionTool>) {
    val colors = FitnexTheme.colors
    Text(stringResource(R.string.tools_title), style = FitnexTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.padding(top = Spacing.sm))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        tools.forEach { tool ->
            GlassCard(onClick = tool.onClick, modifier = Modifier.weight(1f).height(118.dp).testTag(tool.tag), contentPadding = PaddingValues(Spacing.sm)) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(tool.icon, null, tint = colors.textPrimary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(Spacing.sm))
                    Text(tool.label, style = FitnexTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

/**
 * Adults only: estimates a daily target from the profile (editable here) and saves it, or lets the
 * user type their own. Sex is asked here because the formula needs it; it is not stored.
 */
@Composable
internal fun CalorieCalculatorSheet(
    defaults: CalculatorDefaults,
    currentGoal: Int?,
    locale: Locale,
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FitnexTheme.colors
    var sex by rememberSaveable { mutableStateOf<CalculatorSex?>(null) }
    var age by rememberSaveable { mutableStateOf(defaults.ageYears?.toString().orEmpty()) }
    var weight by rememberSaveable { mutableStateOf(defaults.weightKg?.let { fmt(it, Locale.ROOT) }.orEmpty()) }
    var height by rememberSaveable { mutableStateOf(defaults.heightCm?.let { fmt(it, Locale.ROOT) }.orEmpty()) }
    var activity by rememberSaveable { mutableStateOf(defaults.activity ?: ActivityLevel.MODERATE) }
    var aim by rememberSaveable { mutableStateOf(CalorieAim.MAINTAIN) }
    var manual by rememberSaveable { mutableStateOf(currentGoal?.toString().orEmpty()) }

    fun num(s: String) = s.replace(',', '.').toFloatOrNull()
    val a = age.toIntOrNull()?.takeIf { it in 18..100 }
    val w = num(weight)?.takeIf { it in 30f..300f }
    val h = num(height)?.takeIf { it in 120f..230f }
    val s = sex
    val result = if (s != null && a != null && w != null && h != null) CalorieMath.dailyTarget(s, a, w, h, activity, aim) else null
    val manualValue = manual.toIntOrNull()?.takeIf { it in CalorieMath.GOAL_RANGE }
    val activityLabels = ActivityLevel.entries.associateWith { it.label() }
    val aimLabels = CalorieAim.entries.associateWith { it.label() }

    FitnexBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.calc_title)) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.calc_body), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            Text(stringResource(R.string.calc_sex), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary)
            val sexLabels = CalculatorSex.entries.associateWith { it.label() }
            com.fitnex.core.designsystem.component.FitnexChipGroup(
                CalculatorSex.entries, { it == s }, { sex = it }, { sexLabels.getValue(it) }, Modifier.testTag("calc_sex"),
            )
            if (s == null) Text(stringResource(R.string.calc_sex_hint), style = FitnexTheme.typography.bodySmall, color = colors.warning)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitnexTextField(age, { age = it.filter(Char::isDigit).take(3) }, stringResource(R.string.calc_age), Modifier.weight(1f).testTag("calc_age"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                FitnexTextField(weight, { weight = it.take(5) }, stringResource(R.string.calc_weight), Modifier.weight(1f).testTag("calc_weight"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                FitnexTextField(height, { height = it.take(5) }, stringResource(R.string.calc_height), Modifier.weight(1f).testTag("calc_height"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Text(stringResource(R.string.calc_activity), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary)
            com.fitnex.core.designsystem.component.FitnexChipGroup(
                ActivityLevel.entries, { it == activity }, { activity = it }, { activityLabels.getValue(it) },
            )
            Text(stringResource(R.string.calc_aim), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary)
            com.fitnex.core.designsystem.component.FitnexChipGroup(
                CalorieAim.entries, { it == aim }, { aim = it }, { aimLabels.getValue(it) },
            )
            GlassCard(Modifier.fillMaxWidth().testTag("calc_result"), contentPadding = PaddingValues(Spacing.lg)) {
                if (result != null) {
                    Text(stringResource(R.string.calc_result, "%,d".format(locale, result)), style = FitnexTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = colors.accent)
                    val m = CalorieMath.macros(result)
                    Text(stringResource(R.string.calc_macros, m.proteinG, m.carbsG, m.fatG), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
                } else {
                    Text(stringResource(R.string.calc_missing), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
            FitnexPrimaryButton(
                stringResource(R.string.calc_save), { result?.let(onSave) }, Modifier.fillMaxWidth().testTag("calc_save"), enabled = result != null,
            )
            FitnexTextField(
                manual, { manual = it.filter(Char::isDigit).take(4) }, stringResource(R.string.calc_manual), Modifier.fillMaxWidth().testTag("calc_manual"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                errorText = if (manual.isNotEmpty() && manualValue == null) stringResource(R.string.calc_manual_range) else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                com.fitnex.core.designsystem.component.FitnexSecondaryButton(
                    stringResource(R.string.calc_manual_save), { manualValue?.let(onSave) }, Modifier.weight(1f).testTag("calc_manual_save"),
                )
                if (currentGoal != null) {
                    com.fitnex.core.designsystem.component.FitnexSecondaryButton(stringResource(R.string.calc_clear), { onSave(null) }, Modifier.weight(1f).testTag("calc_clear"))
                }
            }
        }
    }
}

@Composable
private fun CalculatorSex.label(): String = stringResource(if (this == CalculatorSex.MALE) R.string.calc_male else R.string.calc_female)

@Composable
private fun CalorieAim.label(): String = stringResource(
    when (this) {
        CalorieAim.LOSE -> R.string.calc_aim_lose
        CalorieAim.MAINTAIN -> R.string.calc_aim_maintain
        CalorieAim.GAIN -> R.string.calc_aim_gain
    },
)

@Composable
private fun ActivityLevel.label(): String = stringResource(
    when (this) {
        ActivityLevel.SEDENTARY -> R.string.calc_act_sedentary
        ActivityLevel.LIGHT -> R.string.calc_act_light
        ActivityLevel.MODERATE -> R.string.calc_act_moderate
        ActivityLevel.ACTIVE -> R.string.calc_act_active
        ActivityLevel.VERY_ACTIVE -> R.string.calc_act_very_active
    },
)
