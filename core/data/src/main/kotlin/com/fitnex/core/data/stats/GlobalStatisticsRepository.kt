package com.fitnex.core.data.stats

import com.fitnex.core.data.catalog.CatalogSeeder
import com.fitnex.core.data.training.toModel
import com.fitnex.core.database.dao.CatalogDao
import com.fitnex.core.database.dao.SessionDao
import com.fitnex.core.database.relation.SessionWithSets
import com.fitnex.core.model.CompletedSession
import com.fitnex.core.model.ExerciseMuscle
import com.fitnex.core.model.ExerciseStats
import com.fitnex.core.model.GlobalStats
import com.fitnex.core.model.StatsCalculator
import com.fitnex.core.model.StatsPeriod
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Spec §20: statistics computed from real, finished sessions; updates whenever a set is saved. */
interface GlobalStatisticsRepository {
    fun observeStats(userId: String, period: StatsPeriod): Flow<GlobalStats>
    fun observeExerciseStats(userId: String, exerciseId: String): Flow<ExerciseStats>
    fun observeCompletedSessions(userId: String): Flow<List<CompletedSession>>
}

@Singleton
class RoomGlobalStatisticsRepository @Inject constructor(
    private val sessions: SessionDao,
    private val catalog: CatalogDao,
    private val seeder: CatalogSeeder,
    private val clock: Clock,
) : GlobalStatisticsRepository {

    override fun observeCompletedSessions(userId: String): Flow<List<CompletedSession>> =
        sessions.observeAllFinished(userId).map { rows -> rows.map { it.toCompleted() }.filter { it.sets.isNotEmpty() } }

    override fun observeStats(userId: String, period: StatsPeriod): Flow<GlobalStats> =
        combine(observeCompletedSessions(userId), catalog.observeAllLinks(), catalog.observeMuscles()) { done, links, muscles ->
            StatsCalculator.compute(
                period = period,
                today = LocalDate.now(clock),
                zone = clock.zone,
                all = done,
                linksByExercise = links.groupBy({ it.exerciseId }) { ExerciseMuscle(it.muscleId, it.involvement) },
                muscles = muscles.associate { it.id to it.toModel() },
            )
        }.onStart { seeder.ensureSeeded() }

    override fun observeExerciseStats(userId: String, exerciseId: String): Flow<ExerciseStats> =
        combine(observeCompletedSessions(userId), catalog.observeExerciseNames()) { done, names ->
            val fallback = names.firstOrNull { it.id == exerciseId }?.name ?: exerciseId
            StatsCalculator.exerciseStats(exerciseId, fallback, done, clock.zone)
        }.onStart { seeder.ensureSeeded() }

    private fun SessionWithSets.toCompleted() = CompletedSession(
        id = session.id,
        routineName = session.workoutNameSnapshot,
        startedAt = session.startedAt,
        duration = Duration.ofSeconds(session.durationSeconds ?: 0),
        sets = sets.filter { it.sync.deletedAt == null }.map { it.toModel() },
    )
}
