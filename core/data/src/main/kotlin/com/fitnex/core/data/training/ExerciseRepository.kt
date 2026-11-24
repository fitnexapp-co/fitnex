package com.fitnex.core.data.training

import com.fitnex.core.data.catalog.CatalogSeeder
import com.fitnex.core.database.dao.CatalogDao
import com.fitnex.core.database.entity.FavoriteExerciseEntity
import com.fitnex.core.database.entity.MuscleEntity
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.database.relation.ExerciseWithMuscles
import com.fitnex.core.model.Exercise
import com.fitnex.core.model.ExerciseListItem
import com.fitnex.core.model.ExerciseMuscle
import com.fitnex.core.model.Muscle
import com.fitnex.core.model.SyncStatus
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

interface ExerciseRepository {
    fun observeLibrary(userId: String): Flow<List<ExerciseListItem>>
    fun observeExercise(id: String): Flow<Exercise?>
    fun observeMuscles(): Flow<List<Muscle>>
    fun observeIsFavorite(userId: String, exerciseId: String): Flow<Boolean>
    suspend fun setFavorite(userId: String, exerciseId: String, favorite: Boolean)
    suspend fun getExercises(ids: List<String>): List<Exercise>

    /** Saves an exercise the user created (only visible to them; synced with their data). Returns its id. */
    suspend fun createCustom(userId: String, draft: CustomExerciseDraft): String = error("Not supported")

    /** Hides a custom exercise; logged sets keep their name snapshot. */
    suspend fun deleteCustom(exerciseId: String): Unit = error("Not supported")
}

/** What the "Crear ejercicio" form collects. */
data class CustomExerciseDraft(
    val name: String,
    val description: String,
    val equipment: Set<com.fitnex.core.model.Equipment>,
    val type: com.fitnex.core.model.ExerciseType,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String> = emptyList(),
    /** How sets are logged; null = derived from type and equipment. */
    val tracking: com.fitnex.core.model.Tracking? = null,
)

@Singleton
class RoomExerciseRepository @Inject constructor(
    private val dao: CatalogDao,
    private val seeder: CatalogSeeder,
    private val clock: Clock,
    private val ids: com.fitnex.core.common.time.IdGenerator,
) : ExerciseRepository {

    override suspend fun createCustom(userId: String, draft: CustomExerciseDraft): String {
        require(draft.name.isNotBlank()) { "Exercise needs a name" }
        require(draft.primaryMuscles.isNotEmpty()) { "Exercise needs a main muscle" }
        val id = "custom_" + ids.newId()
        val now = clock.instant()
        dao.upsertExercises(
            listOf(
                com.fitnex.core.database.entity.ExerciseEntity(
                    id = id, name = draft.name.trim().take(60), description = draft.description.trim().take(400),
                    instructions = emptyList(), techniqueTips = emptyList(), commonMistakes = emptyList(),
                    equipment = draft.equipment.ifEmpty { setOf(com.fitnex.core.model.Equipment.BODYWEIGHT) },
                    type = draft.type, difficulty = com.fitnex.core.model.Difficulty.BEGINNER, mediaUri = null,
                    cameraRepSupported = false, ownerUserId = userId, sync = SyncMeta(updatedAt = now),
                    tracking = draft.tracking ?: com.fitnex.core.model.TrackingRules.forCustom(draft.type, draft.equipment),
                ),
            ),
        )
        val primary = draft.primaryMuscles.distinct()
        dao.upsertExerciseMuscles(
            primary.map { com.fitnex.core.database.entity.ExerciseMuscleEntity(id, it, com.fitnex.core.model.MuscleInvolvement.PRIMARY) } +
                (draft.secondaryMuscles.distinct() - primary.toSet()).map { com.fitnex.core.database.entity.ExerciseMuscleEntity(id, it, com.fitnex.core.model.MuscleInvolvement.SECONDARY) },
        )
        return id
    }

    override suspend fun deleteCustom(exerciseId: String) = dao.softDeleteCustom(exerciseId, clock.instant())

    override fun observeLibrary(userId: String): Flow<List<ExerciseListItem>> =
        combine(dao.observeExercises(userId), dao.observeMuscles(), dao.observeFavoriteIds(userId)) { exercises, muscles, favs ->
            val byId = muscles.associate { it.id to it.toModel() }
            val favorites = favs.toSet()
            exercises.map { row ->
                val exercise = row.toModel()
                ExerciseListItem(exercise, exercise.primaryMuscleIds.mapNotNull(byId::get), exercise.id in favorites)
            }
        }.onStart { seeder.ensureSeeded() }

    override fun observeExercise(id: String): Flow<Exercise?> =
        dao.observeExercise(id).map { it?.toModel() }.onStart { seeder.ensureSeeded() }

    override fun observeMuscles(): Flow<List<Muscle>> =
        dao.observeMuscles().map { list -> list.map { it.toModel() } }.onStart { seeder.ensureSeeded() }

    override fun observeIsFavorite(userId: String, exerciseId: String) = dao.observeIsFavorite(userId, exerciseId)

    override suspend fun setFavorite(userId: String, exerciseId: String, favorite: Boolean) {
        val now = clock.instant()
        dao.upsertFavorite(
            FavoriteExerciseEntity(
                userId = userId,
                exerciseId = exerciseId,
                sync = if (favorite) SyncMeta(updatedAt = now)
                else SyncMeta(updatedAt = now, syncStatus = SyncStatus.PENDING_DELETE, deletedAt = now),
            ),
        )
    }

    override suspend fun getExercises(ids: List<String>): List<Exercise> {
        if (ids.isEmpty()) return emptyList()
        seeder.ensureSeeded()
        val found = dao.getExercises(ids).associateBy { it.exercise.id }
        return ids.mapNotNull { found[it]?.toModel() } // keep the caller's order
    }
}

internal fun MuscleEntity.toModel() = Muscle(id = id, name = name, function = function, region = region)

internal fun ExerciseWithMuscles.toModel() = Exercise(
    id = exercise.id,
    name = exercise.name,
    description = exercise.description,
    instructions = exercise.instructions,
    techniqueTips = exercise.techniqueTips,
    commonMistakes = exercise.commonMistakes,
    equipment = exercise.equipment,
    type = exercise.type,
    difficulty = exercise.difficulty,
    mediaUri = exercise.mediaUri,
    cameraRepSupported = exercise.cameraRepSupported,
    isCustom = exercise.ownerUserId != null,
    muscles = muscles.sortedBy { it.involvement.ordinal }.map { ExerciseMuscle(it.muscleId, it.involvement) },
    tracking = exercise.tracking,
    aliases = exercise.searchTerms.split('|').filter { it.isNotBlank() },
)
