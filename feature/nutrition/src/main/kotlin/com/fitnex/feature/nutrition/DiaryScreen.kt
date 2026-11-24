package com.fitnex.feature.nutrition

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexConfirmDialog
import com.fitnex.core.designsystem.component.FitnexSectionHero
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexTonalButton
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.GlassIconButton
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.ProfileHeaderAction
import com.fitnex.core.designsystem.component.SettingsDivider
import com.fitnex.core.designsystem.component.SettingsRow
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.FoodEntry
import com.fitnex.core.model.Meal
import com.fitnex.core.model.MealType
import com.fitnex.core.model.NutritionLimits
import com.fitnex.core.model.NutritionMath
import com.fitnex.feature.nutrition.photo.MealPhotoStore
import com.fitnex.feature.nutrition.ui.allergenLabel
import com.fitnex.feature.nutrition.ui.fmt
import com.fitnex.feature.nutrition.ui.label
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Diario de nutrición, kept short: the day's totals in one row, water in one row, then each meal
 * with its own "+ Añadir" and photo button. The weekly summary is a header icon.
 */
@Composable
internal fun DiaryScreen(
    state: DiaryUiState,
    vm: NutritionViewModel,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onAddFood: (MealType) -> Unit,
    onOpenWeek: () -> Unit,
    onNewRecipe: () -> Unit = {},
    onOpenPlan: () -> Unit = {},
) {
    val locale = rememberAppLocale()
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    var deleting by remember { mutableStateOf<FoodEntry?>(null) }
    var calculator by rememberSaveable { mutableStateOf(false) }
    val photos = rememberMealPhotoActions(vm)

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        FitnexSectionHero(
            title = stringResource(R.string.nutrition_title),
            subtitle = stringResource(R.string.nutrition_subtitle),
            overline = stringResource(R.string.nutrition_overline),
            overlineIcon = FitnexIcons.Nutrition,
            actions = { ProfileHeaderAction(onOpenSettings, Modifier.testTag("open_settings")) },
        )
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DateBar(state, locale, vm)
            val day = state.day
            if (state.loading || day == null) {
                LoadingState()
            } else {
                // Most used first: today at a glance, water, then the meals to log. Tools and the
                // daily tip come after, so they never push the diary down.
                CaloriesCard(state, locale, onSetGoal = { calculator = true })
                Water(state, locale, vm)
                Text(stringResource(R.string.meals_title), style = FitnexTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = FitnexTheme.colors.textPrimary, modifier = Modifier.padding(top = Spacing.sm))
                MealType.entries.forEach { type ->
                    MealSection(
                        type, day.meal(type), state, locale,
                        onAdd = { onAddFood(type) },
                        onPhoto = { photos.choose(type) },
                        photoError = photos.error?.takeIf { it.first == type }?.second,
                        onEntry = { e -> if (e.foodId != null && state.foods[e.foodId] != null) editing = e else deleting = e },
                        onAnalyze = vm::analyze,
                    )
                }
                ToolsGrid(
                    buildList {
                        add(NutritionTool(FitnexIcons.Cutlery, stringResource(R.string.tool_plan), "tool_plan", onOpenPlan))
                        add(NutritionTool(FitnexIcons.Chart, stringResource(R.string.tool_stats), "tool_stats", onOpenWeek))
                        if (state.audience.showKcal) add(NutritionTool(FitnexIcons.Calculator, stringResource(R.string.tool_calculator), "tool_calculator") { calculator = true })
                    },
                )
                TipCard(state.date)
                Text(stringResource(R.string.estimates_note), style = FitnexTheme.typography.bodySmall, color = FitnexTheme.colors.textDisabled)
            }
        }
    }

    photos.Sheet()

    if (calculator && state.audience.showKcal) {
        CalorieCalculatorSheet(
            defaults = state.calculator, currentGoal = state.kcalGoal, locale = locale,
            onSave = { vm.setKcalGoal(it); calculator = false },
            onDismiss = { calculator = false },
        )
    }

    val analysis by vm.photoAnalysis.collectAsStateWithLifecycle()
    analysis?.let { ui ->
        PhotoAnalysisSheet(
            ui = ui, audience = state.audience,
            onSave = { kept -> vm.saveDetected(ui.mealType, kept) },
            onConsent = { vm.grantAiConsent(); state.day?.meal(ui.mealType)?.let(vm::analyze) },
            onRetry = { state.day?.meal(ui.mealType)?.let(vm::analyze) },
            onDismiss = vm::dismissAnalysis,
        )
    }
    editing?.let { e ->
        val food = state.foods.getValue(e.foodId!!)
        PortionSheet(
            food = food, initialGrams = e.grams, audience = state.audience,
            confirmLabel = stringResource(R.string.portion_save),
            onConfirm = { g, p -> vm.updatePortion(e, food, g, p); editing = null },
            onDismiss = { editing = null },
            onDelete = { editing = null; deleting = e },
        )
    }
    deleting?.let { e ->
        FitnexConfirmDialog(
            title = stringResource(R.string.entry_delete_title, e.name),
            message = stringResource(R.string.entry_delete_message),
            confirmLabel = stringResource(R.string.entry_delete),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = { vm.deleteEntry(e); deleting = null },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }
}

/**
 * Meal photos: camera or gallery, straight into the meal that asked. The camera permission is
 * requested first: an app that declares CAMERA may not open the system camera without it
 * (that was the crash on "Cámara").
 */
private class MealPhotoActions(
    val choose: (MealType) -> Unit,
    val error: Pair<MealType, Int>?,
    val Sheet: @Composable () -> Unit,
)

@Composable
private fun rememberMealPhotoActions(vm: NutritionViewModel): MealPhotoActions {
    val context = LocalContext.current
    val colors = FitnexTheme.colors
    val store = remember { EntryPointAccessors.fromApplication(context.applicationContext, PhotoStoreEntryPoint::class.java).mealPhotoStore() }
    val scope = rememberCoroutineScope()
    var choosing by rememberSaveable { mutableStateOf<MealType?>(null) }
    var target by rememberSaveable { mutableStateOf<MealType?>(null) }
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Pair<MealType, Int>?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile?.let(::File)
        val meal = target
        if (ok && f != null && f.length() > 0 && meal != null) vm.attachPhoto(meal, f.absolutePath) else f?.let(store::discard)
        cameraFile = null
    }
    fun openCamera(meal: MealType) {
        val (file, uri) = store.newCameraTarget()
        cameraFile = file.absolutePath
        target = meal
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            store.discard(file); cameraFile = null; error = meal to R.string.photo_no_camera
        } catch (e: SecurityException) {
            store.discard(file); cameraFile = null; error = meal to R.string.photo_camera_denied
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val meal = target ?: return@rememberLauncherForActivityResult
        if (granted) openCamera(meal) else error = meal to R.string.photo_camera_denied
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val meal = target
        if (uri != null && meal != null) {
            scope.launch {
                val path = store.importFromGallery(uri)
                if (path != null) vm.attachPhoto(meal, path) else error = meal to R.string.photo_failed
            }
        }
    }

    return MealPhotoActions(
        choose = { meal -> error = null; choosing = meal },
        error = error,
        Sheet = {
            choosing?.let { meal ->
                FitnexBottomSheet(onDismiss = { choosing = null }, title = stringResource(R.string.photo_cd, meal.label())) {
                    SettingsRow(
                        title = stringResource(R.string.photo_take), icon = FitnexIcons.Camera, chevron = false,
                        onClick = {
                            choosing = null
                            target = meal
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera(meal)
                            else permission.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.testTag("photo_camera"),
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = stringResource(R.string.photo_choose), icon = FitnexIcons.Image, chevron = false,
                        onClick = {
                            choosing = null
                            target = meal
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.testTag("photo_gallery"),
                    )
                    Text(stringResource(R.string.photo_saved_note), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.sm))
                }
            }
        },
    )
}

@Composable
private fun DateBar(state: DiaryUiState, locale: Locale, vm: NutritionViewModel) {
    val colors = FitnexTheme.colors
    val label = if (state.isToday) stringResource(R.string.today) + ", " + state.date.format(DateTimeFormatter.ofPattern("d MMM", locale))
    else state.date.format(DateTimeFormatter.ofPattern("EEEE d MMM", locale)).replaceFirstChar { it.titlecase(locale) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::previousDay, modifier = Modifier.testTag("day_previous")) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_day), tint = colors.textPrimary)
        }
        Text(label, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f).testTag("day_label"))
        if (!state.isToday) TextButton(onClick = vm::goToToday) { Text(stringResource(R.string.today), color = colors.accent) }
        IconButton(onClick = vm::nextDay, enabled = !state.isToday, modifier = Modifier.testTag("day_next")) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_day), tint = if (state.isToday) colors.textDisabled else colors.textPrimary)
        }
    }
}



/** Water in one row: what you drank and two quick buttons. */
@Composable
private fun Water(state: DiaryUiState, locale: Locale, vm: NutritionViewModel) {
    val colors = FitnexTheme.colors
    val total = state.water.sumOf { it.ml }
    GlassCard(Modifier.fillMaxWidth().testTag("water_card"), contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FitnexIcons.Drop, null, tint = colors.accent, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                Text(stringResource(R.string.water_total, "%,d".format(locale, total)), style = FitnexTheme.typography.titleLarge, color = colors.textPrimary, modifier = Modifier.testTag("water_total"))
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.water_glasses, total / 250, total / 250), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
            NutritionLimits.WATER_QUICK_ML.forEach { ml ->
                FitnexTonalButton(stringResource(R.string.water_add, ml), { vm.addWater(ml) }, Modifier.padding(start = Spacing.xs).testTag("water_add_$ml"))
            }
        }
        if (state.water.isNotEmpty()) {
            TextButton(onClick = vm::undoWater, modifier = Modifier.testTag("water_undo")) { Text(stringResource(R.string.water_undo), color = colors.accent) }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal interface PhotoStoreEntryPoint {
    fun mealPhotoStore(): MealPhotoStore
}

@Composable
private fun MealSection(
    type: MealType,
    meal: Meal?,
    state: DiaryUiState,
    locale: Locale,
    onAdd: () -> Unit,
    onPhoto: () -> Unit,
    photoError: Int?,
    onEntry: (FoodEntry) -> Unit,
    onAnalyze: (Meal) -> Unit,
) {
    val colors = FitnexTheme.colors
    val items = meal?.items.orEmpty()
    GlassCard(Modifier.fillMaxWidth().testTag("meal_${type.name}"), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(type.label(), style = FitnexTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                if (items.isNotEmpty()) {
                    val p = items.fold(0f) { a, e -> a + e.nutrients.proteinG }
                    Text("${stringResource(R.string.protein)} ${fmt(p, locale)} g", style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
            IconButton(onClick = onPhoto, modifier = Modifier.testTag("meal_photo_${type.name}")) {
                Icon(FitnexIcons.Camera, stringResource(R.string.photo_add_cd, type.label()), tint = colors.textSecondary)
            }
            TextButton(onClick = onAdd, modifier = Modifier.testTag("meal_add_${type.name}")) {
                Icon(Icons.Rounded.Add, null, tint = colors.accent)
                Text(stringResource(R.string.meal_add), color = colors.accent)
            }
        }
        photoError?.let { Text(stringResource(it), style = FitnexTheme.typography.bodySmall, color = colors.warning, modifier = Modifier.padding(end = Spacing.sm)) }
        meal?.imageReference?.let { path ->
            MealPhoto(path, type.label())
            FitnexSecondaryButton(stringResource(R.string.photo_analyze), { onAnalyze(meal) }, Modifier.fillMaxWidth().padding(end = Spacing.sm, bottom = Spacing.xs).testTag("analyze_${type.name}"))
        }
        if (items.isEmpty() && meal?.imageReference == null) {
            Text(stringResource(R.string.meal_empty), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
        }
        items.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = colors.divider)
            EntryRow(e, state, locale, onClick = { onEntry(e) })
        }
    }
}

@Composable
private fun EntryRow(e: FoodEntry, state: DiaryUiState, locale: Locale, onClick: () -> Unit) {
    val colors = FitnexTheme.colors
    val food = e.foodId?.let { state.foods[it] }
    val conflicts = food?.let { NutritionMath.conflicts(it, state.audience.allergies) }.orEmpty()
    // The whole row opens the portion editor.
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(end = Spacing.sm).padding(vertical = Spacing.sm).testTag("entry_${e.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(e.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(e.portion, style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        Text(
            stringResource(R.string.entry_macros, fmt(e.nutrients.proteinG, locale), fmt(e.nutrients.carbsG, locale), fmt(e.nutrients.fatG, locale)) +
                if (state.audience.showKcal) " · ≈ ${fmt(e.nutrients.kcal, locale)} kcal" else "",
            style = FitnexTheme.typography.bodySmall, color = colors.textSecondary,
        )
        if (food == null) Text(stringResource(R.string.entry_custom), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
        if (conflicts.isNotEmpty()) {
            Text(stringResource(R.string.entry_allergen, conflicts.map { allergenLabel(it) }.joinToString()), style = FitnexTheme.typography.bodySmall, color = colors.warning)
        }
    }
}
@Composable
private fun MealPhoto(path: String, mealName: String) {
    val bitmap by produceState<Bitmap?>(null, path) { value = MealPhotoStore.loadThumbnail(path) }
    bitmap?.let {
        Image(
            it.asImageBitmap(), contentDescription = stringResource(R.string.photo_cd, mealName), contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().padding(end = Spacing.sm).height(170.dp).padding(vertical = Spacing.xs).clip(RoundedCornerShape(18.dp)).testTag("meal_photo"),
        )
    }
}
