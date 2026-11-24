package com.fitnex.core.data.catalog

import android.content.Context
import com.fitnex.core.database.dao.CatalogDao
import com.fitnex.core.database.entity.ExerciseEntity
import com.fitnex.core.database.entity.ExerciseMuscleEntity
import com.fitnex.core.database.entity.MuscleEntity
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.datastore.UserPreferencesDataSource
import com.fitnex.core.model.AnatomicalRegion
import com.fitnex.core.model.Difficulty
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.ExerciseType
import com.fitnex.core.model.MuscleInvolvement
import com.fitnex.core.model.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import com.fitnex.core.model.Tracking
import kotlinx.serialization.json.Json

/** Source of the catalogue JSON; an interface so tests can feed a small catalogue. */
fun interface CatalogSource {
    fun read(): String
}

class AssetCatalogSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : CatalogSource {
    override fun read(): String = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }

    companion object {
        const val ASSET_PATH = "catalog/catalog.json"
    }
}

/**
 * Writes the built-in exercise and muscle catalogue into Room. Runs once per catalogue
 * version (bump `version` in catalog.json to ship changes); safe to call repeatedly and from
 * many collectors at once thanks to the mutex.
 */
@Singleton
class CatalogSeeder @Inject constructor(
    private val source: CatalogSource,
    private val dao: CatalogDao,
    private val preferences: UserPreferencesDataSource,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Once the catalogue is in Room for this process, later calls cost nothing (no re-parse of ~600 exercises). */
    @Volatile private var upToDate = false

    suspend fun ensureSeeded() {
        if (upToDate) return
        mutex.withLock {
            if (upToDate) return@withLock
            val text = source.read()
            val stored = preferences.catalogVersion.first()
            // Cold starts only read the version number; the ~600 exercises are parsed only to (re)write them.
            val version = peekVersion(text)
            if (version == null || stored < version || dao.builtInExerciseCount() == 0) {
                val catalog = parse(text)
                write(catalog)
                preferences.setCatalogVersion(catalog.version)
            }
            upToDate = true
        }
    }

    private suspend fun write(catalog: CatalogDto) {
        val now = clock.instant()
        val muscleIds = catalog.muscles.map { it.id }.toSet()
        val muscles = catalog.muscles.map {
            MuscleEntity(it.id, it.name, it.function, AnatomicalRegion.valueOf(it.region), model2dId = it.id, model3dId = it.id)
        }
        val exercises = catalog.exercises.map { e ->
            ExerciseEntity(
                id = e.id, name = e.name, description = e.description, instructions = e.instructions,
                techniqueTips = e.tips, commonMistakes = e.mistakes,
                equipment = e.equipment.mapTo(mutableSetOf()) { Equipment.valueOf(it) },
                type = ExerciseType.valueOf(e.type), difficulty = Difficulty.valueOf(e.difficulty),
                mediaUri = when {
                    e.media -> "exercise_media/${e.id}"
                    // A very similar variant's photo, shown with a "reference photo" label.
                    e.mediaFrom != null -> "exercise_media/${e.mediaFrom}$REFERENCE_SUFFIX"
                    else -> null
                },
                cameraRepSupported = e.camera, ownerUserId = null,
                // Built-in rows are never synced: every install ships the same catalogue.
                sync = SyncMeta(updatedAt = now, syncStatus = SyncStatus.SYNCED),
                tracking = runCatching { Tracking.valueOf(e.tracking) }.getOrDefault(Tracking.WEIGHT_REPS),
                searchTerms = e.aliases.joinToString("|"),
            )
        }
        val links = catalog.exercises.flatMap { e ->
            fun link(ids: List<String>, inv: MuscleInvolvement) = ids.map {
                require(it in muscleIds) { "Exercise ${e.id} references unknown muscle $it" }
                ExerciseMuscleEntity(e.id, it, inv)
            }
            link(e.primary, MuscleInvolvement.PRIMARY) + link(e.secondary, MuscleInvolvement.SECONDARY) +
                link(e.stabilizer, MuscleInvolvement.STABILIZER)
        }.distinctBy { it.exerciseId to it.muscleId }
        dao.replaceCatalog(muscles, exercises, links)
    }

    companion object {
        /** Appended to a mediaUri whose photos show a similar variant, not this exact exercise. */
        const val REFERENCE_SUFFIX = "#ref"
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): CatalogDto = json.decodeFromString(CatalogDto.serializer(), text)

        private val VERSION = Regex("\"version\"\\s*:\\s*(\\d+)")

        /** The catalogue version from the top of the file, without parsing the whole thing. */
        fun peekVersion(text: String): Int? = VERSION.find(text.take(400))?.groupValues?.get(1)?.toIntOrNull()
    }
}

@Serializable
data class CatalogDto(val version: Int, val muscles: List<MuscleDto>, val exercises: List<ExerciseDto>)

@Serializable
data class MuscleDto(val id: String, val name: String, val region: String, val function: String)

@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    val type: String,
    val difficulty: String,
    val camera: Boolean = false,
    /** Photos in assets/exercise_media/<id>/0.jpg (start) and 1.jpg (end), public domain. */
    val media: Boolean = false,
    /** Folder in assets/exercise_media with photos of a very similar variant (catalogue v6). */
    val mediaFrom: String? = null,
    val equipment: List<String>,
    val description: String,
    val instructions: List<String>,
    val tips: List<String> = emptyList(),
    val mistakes: List<String> = emptyList(),
    val primary: List<String>,
    val secondary: List<String> = emptyList(),
    val stabilizer: List<String> = emptyList(),
    /** WEIGHT_REPS, REPS, TIME or TIME_DISTANCE (catalogue v4). */
    val tracking: String = "WEIGHT_REPS",
    /** English name, common names and tags, for search (catalogue v4). */
    val aliases: List<String> = emptyList(),
)
