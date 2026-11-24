package com.fitnex.feature.nutrition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.nutrition.FoodCatalogRepository
import com.fitnex.core.data.nutrition.NutritionRepository
import com.fitnex.core.data.nutrition.WaterEntry
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.model.Food
import com.fitnex.core.model.FoodCategory
import com.fitnex.core.model.FoodEntry
import com.fitnex.core.model.MealType
import com.fitnex.core.model.Nutrients
import com.fitnex.core.model.NutritionDay
import com.fitnex.core.model.NutritionMath
import com.fitnex.core.model.NutritionStatsCalculator
import com.fitnex.core.model.NutritionWeek
import com.fitnex.core.model.Recipe
import com.fitnex.core.model.RecipeItem
import com.fitnex.core.model.RecipeMath
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PhotoAnalysisUi(
    val mealType: MealType,
    val loading: Boolean = true,
    val result: com.fitnex.core.model.FoodPhotoAnalysis? = null,
    val failure: com.fitnex.core.model.AiFailure? = null,
)

/** What the user can see for their age: minors never get calorie figures (spec §4, §6). */
data class NutritionAudience(val showKcal: Boolean = false, val allergies: Set<String> = emptySet())

data class DiaryUiState(
    val loading: Boolean = true,
    val date: LocalDate,
    val today: LocalDate,
    val day: NutritionDay? = null,
    val water: List<WaterEntry> = emptyList(),
    val audience: NutritionAudience = NutritionAudience(),
    /** Catalog foods of the day's entries, to edit portions and flag allergens. */
    val foods: Map<String, Food> = emptyMap(),
    /** Adult's daily calorie target (kcal); null = not set. Always null for minors. */
    val kcalGoal: Int? = null,
    /** Profile values that pre-fill the calorie calculator (null = not in the profile). */
    val calculator: CalculatorDefaults = CalculatorDefaults(),
) {
    val isToday: Boolean get() = date == today
    val macroTargets: com.fitnex.core.model.MacroTargets? get() = kcalGoal?.let(com.fitnex.core.model.CalorieMath::macros)
}

data class CalculatorDefaults(
    val ageYears: Int? = null,
    val weightKg: Float? = null,
    val heightCm: Float? = null,
    val activity: com.fitnex.core.model.ActivityLevel? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val profiles: UserProfileRepository,
    private val nutrition: NutritionRepository,
    private val catalog: FoodCatalogRepository,
    private val ai: com.fitnex.core.data.ai.AiRepository,
    private val preferences: com.fitnex.core.datastore.UserPreferencesDataSource,
) : ViewModel() {
    private val today get() = LocalDate.now(clock)
    private val analysis = MutableStateFlow<PhotoAnalysisUi?>(null)
    val photoAnalysis: StateFlow<PhotoAnalysisUi?> = analysis

    /** Sends the meal photo to FITNEX AI (spec §6); the result is reviewed and edited before saving. */
    fun analyze(meal: com.fitnex.core.model.Meal) {
        val path = meal.imageReference ?: return
        analysis.value = PhotoAnalysisUi(meal.type)
        viewModelScope.launch {
            val uid = session.currentUserId.first() ?: return@launch
            analysis.value = when (val r = ai.analyzeFoodPhoto(uid, path)) {
                is com.fitnex.core.data.ai.AiResult.Ok -> PhotoAnalysisUi(meal.type, loading = false, result = r.value)
                is com.fitnex.core.data.ai.AiResult.Failed -> PhotoAnalysisUi(meal.type, loading = false, failure = r.reason)
            }
        }
    }

    /** Saves the foods the user kept, with the grams they chose (nutrients scale with the grams). */
    fun saveDetected(type: MealType, kept: List<Pair<com.fitnex.core.model.DetectedFood, Float>>) {
        analysis.value = null
        launchWithUser { uid ->
            kept.forEach { (f, grams) ->
                val factor = if (f.grams > 0f) grams / f.grams else 1f
                nutrition.addEstimated(uid, date.value, type, f.name, grams, "${grams.toInt()} g", f.nutrients.scaled(factor), f.confidence)
            }
        }
    }

    fun dismissAnalysis() { analysis.value = null }
    fun grantAiConsent() = launchWithUser { profiles.setAiConsent(it, true) }
    private val date = MutableStateFlow(savedState.get<String>(KEY_DATE)?.let(LocalDate::parse) ?: today)

    val state: StateFlow<DiaryUiState> = combine(session.currentUserId.filterNotNull(), date, ::Pair)
        .flatMapLatest { (uid, d) ->
            combine(
                nutrition.observeDay(uid, d),
                nutrition.observeWater(uid, d),
                combine(audience(uid), calculatorDefaults(uid), ::Pair),
                flowOf(catalog.all().associateBy { it.id }),
                preferences.kcalGoal(uid),
            ) { day, water, (aud, calc), foods, goal ->
                DiaryUiState(false, d, today, day, water, aud, foods, kcalGoal = goal.takeIf { aud.showKcal }, calculator = calc)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryUiState(date = date.value, today = today))

    private fun calculatorDefaults(uid: String) = profiles.observe(uid).map { p ->
        CalculatorDefaults(
            ageYears = p?.birthDate?.let { java.time.Period.between(it, today).years },
            weightKg = p?.weightKg, heightCm = p?.heightCm, activity = p?.activityLevel,
        )
    }

    /** Saves (or clears with null) the adult's daily calorie target. Ignored for minors. */
    fun setKcalGoal(kcal: Int?) {
        if (!state.value.audience.showKcal && kcal != null) return
        launchWithUser { preferences.setKcalGoal(it, kcal?.coerceIn(com.fitnex.core.model.CalorieMath.GOAL_RANGE)) }
    }

    private fun audience(uid: String) = profiles.observe(uid).map { p ->
        NutritionAudience(showKcal = p?.ageProfile(today)?.showCalorieEstimates == true, allergies = p?.allergies.orEmpty().toSet())
    }

    fun previousDay() = setDate(date.value.minusDays(1))
    fun nextDay() = setDate(date.value.plusDays(1))
    fun goToToday() = setDate(today)

    private fun setDate(d: LocalDate) {
        date.value = d
        savedState[KEY_DATE] = d.toString()
    }

    fun addWater(ml: Int) = launchWithUser { nutrition.addWater(it, ml) }
    fun undoWater() { state.value.water.lastOrNull()?.let { w -> viewModelScope.launch { nutrition.deleteWater(w.id) } } }
    fun deleteEntry(entry: FoodEntry) { viewModelScope.launch { nutrition.deleteEntry(entry.id) } }
    fun updatePortion(entry: FoodEntry, food: Food, grams: Float, portion: String) {
        viewModelScope.launch { nutrition.updatePortion(entry.id, food, grams, portion) }
    }
    fun attachPhoto(type: MealType, path: String) = launchWithUser { nutrition.attachPhoto(it, date.value, type, path) }

    private fun launchWithUser(block: suspend (String) -> Unit) {
        viewModelScope.launch { session.currentUserId.first()?.let { block(it) } }
    }

    private companion object { const val KEY_DATE = "nutrition_date" }
}

data class AddFoodUiState(
    val loading: Boolean = true,
    val query: String = "",
    val category: FoodCategory? = null,
    val results: List<Food> = emptyList(),
    val recent: List<Food> = emptyList(),
    val audience: NutritionAudience = NutritionAudience(),
    val recipes: List<Recipe> = emptyList(),
    val foods: Map<String, Food> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddFoodViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val clock: Clock,
    private val session: SessionRepository,
    private val profiles: UserProfileRepository,
    private val nutrition: NutritionRepository,
    private val catalog: FoodCatalogRepository,
    /** Packaged foods by barcode (Open Food Facts). */
    val barcode: com.fitnex.core.data.nutrition.BarcodeFoodLookup,
    private val plan: com.fitnex.core.data.nutrition.MealPlanRepository,
) : ViewModel() {
    val date: LocalDate = LocalDate.parse(checkNotNull(savedState.get<String>("date")))
    /** Set when adding to the weekly meal plan (1 = Monday … 7 = Sunday) instead of the diary. */
    val planDay: java.time.DayOfWeek? = savedState.get<Int>("planDay")?.let(java.time.DayOfWeek::of)
    val mealType: MealType = MealType.valueOf(checkNotNull(savedState.get<String>("mealType")))
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<FoodCategory?>(null)
    private val done = Channel<Unit>(Channel.CONFLATED)
    val finished = done.receiveAsFlow()

    val state: StateFlow<AddFoodUiState> = session.currentUserId.filterNotNull()
        .flatMapLatest { uid ->
            val all = catalog.all()
            val byId = all.associateBy { it.id }
            combine(query, category, nutrition.observeRecentFoodIds(uid), profiles.observe(uid), nutrition.observeRecipes(uid)) { q, c, recentIds, p, recipes ->
                AddFoodUiState(
                    loading = false, query = q, category = c,
                    results = NutritionMath.search(all, q, c),
                    recent = recentIds.mapNotNull { byId[it] },
                    audience = NutritionAudience(
                        showKcal = p?.ageProfile(LocalDate.now(clock))?.showCalorieEstimates == true,
                        allergies = p?.allergies.orEmpty().toSet(),
                    ),
                    recipes = recipes,
                    foods = byId,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddFoodUiState())

    fun setQuery(q: String) { query.value = q.take(40) }
    fun setCategory(c: FoodCategory?) { category.value = c }

    fun add(food: Food, grams: Float, portion: String) {
        viewModelScope.launch {
            val uid = session.currentUserId.first() ?: return@launch
            val day = planDay
            if (day != null) plan.addFood(uid, day, mealType, food, grams, portion) else nutrition.addFood(uid, date, mealType, food, grams, portion)
            done.send(Unit)
        }
    }

    fun addRecipe(recipe: Recipe, servings: Float, portion: String) {
        viewModelScope.launch {
            val uid = session.currentUserId.first() ?: return@launch
            val day = planDay
            if (day != null) plan.addRecipe(uid, day, mealType, recipe, servings, portion)
            else nutrition.addRecipe(uid, date, mealType, recipe, RecipeMath.perServing(recipe, state.value.foods), servings, portion)
            done.send(Unit)
        }
    }
}

data class RecipeEditorUiState(
    val loading: Boolean = true,
    val id: String = "",
    val isNew: Boolean = true,
    val name: String = "",
    val servings: Int = 2,
    val items: List<RecipeItem> = emptyList(),
    val foods: Map<String, Food> = emptyMap(),
    val catalog: List<Food> = emptyList(),
    val audience: NutritionAudience = NutritionAudience(),
) {
    val recipe: Recipe get() = Recipe(id, name, servings, items)
    val canSave: Boolean get() = name.isNotBlank() && items.isNotEmpty()
}

@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    clock: Clock,
    ids: com.fitnex.core.common.time.IdGenerator,
    private val session: SessionRepository,
    profiles: UserProfileRepository,
    private val nutrition: NutritionRepository,
    catalog: FoodCatalogRepository,
) : ViewModel() {
    private val recipeId: String? = savedState.get<String>("recipeId")
    private val _state = MutableStateFlow(RecipeEditorUiState(id = recipeId ?: ids.newId(), isNew = recipeId == null))
    val state: StateFlow<RecipeEditorUiState> = _state
    private val done = Channel<Unit>(Channel.CONFLATED)
    val finished = done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val all = catalog.all()
            val existing = recipeId?.let { nutrition.getRecipe(it) }
            val uid = session.currentUserId.first()
            val profile = uid?.let { profiles.get(it) }
            _state.value = _state.value.copy(
                loading = false,
                name = existing?.name ?: "",
                servings = existing?.servings ?: 2,
                items = existing?.items.orEmpty(),
                foods = all.associateBy { it.id },
                catalog = all,
                audience = NutritionAudience(profile?.ageProfile(LocalDate.now(clock))?.showCalorieEstimates == true, profile?.allergies.orEmpty().toSet()),
            )
        }
    }

    fun setName(n: String) { _state.value = _state.value.copy(name = n.take(40)) }
    fun setServings(s: Int) { _state.value = _state.value.copy(servings = s.coerceIn(RecipeMath.SERVINGS)) }
    fun addItem(food: Food, grams: Float) {
        val s = _state.value
        if (s.items.size < RecipeMath.MAX_ITEMS) _state.value = s.copy(items = s.items + RecipeItem(food.id, grams))
    }
    fun updateItem(index: Int, grams: Float) {
        _state.value = _state.value.copy(items = _state.value.items.mapIndexed { i, it -> if (i == index) it.copy(grams = grams) else it })
    }
    fun removeItem(index: Int) { _state.value = _state.value.copy(items = _state.value.items.filterIndexed { i, _ -> i != index }) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val uid = session.currentUserId.first() ?: return@launch
            nutrition.saveRecipe(uid, s.recipe)
            done.send(Unit)
        }
    }

    fun delete() {
        viewModelScope.launch {
            nutrition.deleteRecipe(_state.value.id)
            done.send(Unit)
        }
    }
}

data class WeekUiState(
    val loading: Boolean = true,
    val week: NutritionWeek? = null,
    val audience: NutritionAudience = NutritionAudience(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionWeekViewModel @Inject constructor(
    clock: Clock,
    session: SessionRepository,
    profiles: UserProfileRepository,
    nutrition: NutritionRepository,
    catalog: FoodCatalogRepository,
) : ViewModel() {
    private val end = LocalDate.now(clock)
    private val start = end.minusDays(6)

    val state: StateFlow<WeekUiState> = session.currentUserId.filterNotNull()
        .flatMapLatest { uid ->
            val foods = catalog.all().associateBy { it.id }
            combine(nutrition.observeRange(uid, start, end), nutrition.observeWaterByDay(uid, start, end), profiles.observe(uid)) { meals, water, p ->
                WeekUiState(
                    loading = false,
                    week = NutritionStatsCalculator.week(end, meals, water, foods),
                    audience = NutritionAudience(p?.ageProfile(end)?.showCalorieEstimates == true, p?.allergies.orEmpty().toSet()),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomFoodViewModel @Inject constructor(
    savedState: SavedStateHandle,
    clock: Clock,
    private val session: SessionRepository,
    profiles: UserProfileRepository,
    private val nutrition: NutritionRepository,
) : ViewModel() {
    val date: LocalDate = LocalDate.parse(checkNotNull(savedState.get<String>("date")))
    val mealType: MealType = MealType.valueOf(checkNotNull(savedState.get<String>("mealType")))
    private val done = Channel<Unit>(Channel.CONFLATED)
    val finished = done.receiveAsFlow()

    /** Minors are never asked for calories. */
    val showKcal: StateFlow<Boolean> = session.currentUserId.filterNotNull()
        .flatMapLatest { uid -> profiles.observe(uid).map { it?.ageProfile(LocalDate.now(clock))?.showCalorieEstimates == true } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun save(name: String, grams: Float?, nutrients: Nutrients) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val uid = session.currentUserId.first() ?: return@launch
            nutrition.addCustom(uid, date, mealType, name, grams, nutrients)
            done.send(Unit)
        }
    }
}

data class MealPlanUiState(
    val loading: Boolean = true,
    val day: java.time.DayOfWeek,
    val today: LocalDate,
    /** Every planned item of the week. */
    val items: List<com.fitnex.core.data.nutrition.PlannedItem> = emptyList(),
    val foods: Map<String, Food> = emptyMap(),
    val recipes: Map<String, Recipe> = emptyMap(),
    val audience: NutritionAudience = NutritionAudience(),
    /** Entries added by the last "Añadir al diario" (shown once); null = nothing to show. */
    val copied: Int? = null,
) {
    val dayItems get() = items.filter { it.day == day }
    fun count(d: java.time.DayOfWeek) = items.count { it.day == d }

    /** Nutrients of one planned item, from the catalog or the recipe (null if it no longer exists). */
    fun nutrientsOf(item: com.fitnex.core.data.nutrition.PlannedItem): Nutrients? {
        val food = item.foodId?.let(foods::get)
        val grams = item.grams
        if (food != null && grams != null) return NutritionMath.forGrams(food, grams)
        val recipe = item.recipeId?.let(recipes::get)
        val servings = item.servings
        if (recipe != null && servings != null) return RecipeMath.perServing(recipe, foods).scaled(servings)
        return null
    }

    val dayTotals: Nutrients get() = dayItems.mapNotNull(::nutrientsOf).fold(Nutrients()) { a, n -> a + n }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val clock: Clock,
    private val session: SessionRepository,
    private val profiles: UserProfileRepository,
    private val nutrition: NutritionRepository,
    private val catalog: FoodCatalogRepository,
    private val plan: com.fitnex.core.data.nutrition.MealPlanRepository,
) : ViewModel() {
    private val today get() = LocalDate.now(clock)
    private val day = MutableStateFlow(today.dayOfWeek)
    private val copied = MutableStateFlow<Int?>(null)

    val state: StateFlow<MealPlanUiState> = session.currentUserId.filterNotNull()
        .flatMapLatest { uid ->
            val foods = catalog.all().associateBy { it.id }
            combine(plan.observe(uid), nutrition.observeRecipes(uid), profiles.observe(uid), day, copied) { items, recipes, p, d, c ->
                MealPlanUiState(
                    loading = false, day = d, today = today, items = items, foods = foods,
                    recipes = recipes.associateBy { it.id },
                    audience = NutritionAudience(p?.ageProfile(today)?.showCalorieEstimates == true, p?.allergies.orEmpty().toSet()),
                    copied = c,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealPlanUiState(day = today.dayOfWeek, today = today))

    fun selectDay(d: java.time.DayOfWeek) { day.value = d; copied.value = null }
    fun remove(id: String) { viewModelScope.launch { plan.remove(id) } }
    fun dismissCopied() { copied.value = null }

    /** Copies the selected day's plan into TODAY's diary. */
    fun copyToToday() {
        viewModelScope.launch {
            val uid = session.currentUserId.first() ?: return@launch
            copied.value = plan.copyDayToDiary(uid, day.value, today)
        }
    }
}