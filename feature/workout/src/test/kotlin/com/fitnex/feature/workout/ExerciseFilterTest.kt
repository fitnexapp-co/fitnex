package com.fitnex.feature.workout

import com.fitnex.core.model.AnatomicalRegion
import com.fitnex.core.model.Difficulty
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.Exercise
import com.fitnex.core.model.ExerciseListItem
import com.fitnex.core.model.ExerciseMuscle
import com.fitnex.core.model.ExerciseType
import com.fitnex.core.model.Muscle
import com.fitnex.core.model.MuscleInvolvement
import com.fitnex.feature.workout.library.ExerciseFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseFilterTest {
    private val muscles = listOf(
        Muscle("pectoralis_major", "Pectoral mayor", "", AnatomicalRegion.CHEST),
        Muscle("triceps_brachii", "Tríceps", "", AnatomicalRegion.ARMS),
        Muscle("gluteus_maximus", "Glúteo mayor", "", AnatomicalRegion.GLUTES),
        Muscle("quadriceps", "Cuádriceps", "", AnatomicalRegion.LEGS),
    ).associateBy { it.id }

    private fun ex(
        id: String, name: String, eq: Set<Equipment>, primary: List<String>, secondary: List<String> = emptyList(),
        type: ExerciseType = ExerciseType.STRENGTH, diff: Difficulty = Difficulty.BEGINNER, fav: Boolean = false,
    ) = ExerciseListItem(
        Exercise(
            id, name, "", emptyList(), emptyList(), emptyList(), eq, type, diff, null, false, false,
            primary.map { ExerciseMuscle(it, MuscleInvolvement.PRIMARY) } + secondary.map { ExerciseMuscle(it, MuscleInvolvement.SECONDARY) },
        ),
        primary.mapNotNull(muscles::get), fav,
    )

    private val bench = ex("bench_press", "Press de banca", setOf(Equipment.BARBELL, Equipment.BENCH), listOf("pectoralis_major"), listOf("triceps_brachii"))
    private val pushUp = ex("push_up", "Flexiones", setOf(Equipment.BODYWEIGHT), listOf("pectoralis_major"), type = ExerciseType.CALISTHENICS, fav = true)
    private val squat = ex("goblet_squat", "Sentadilla goblet", setOf(Equipment.DUMBBELL), listOf("quadriceps", "gluteus_maximus"))
    private val all = listOf(bench, pushUp, squat)

    private fun ids(f: ExerciseFilter, mine: Set<Equipment> = emptySet()) = f.apply(all, muscles, mine).map { it.exercise.id }

    @Test
    fun noFilterReturnsEverything() {
        assertEquals(3, ids(ExerciseFilter()).size)
        assertFalse(ExerciseFilter().isActive)
    }

    @Test
    fun searchIgnoresCaseAndAccentsAndMatchesMuscles() {
        assertEquals(listOf("goblet_squat"), ids(ExerciseFilter(query = "SENTADILLA")))
        assertEquals(listOf("goblet_squat"), ids(ExerciseFilter(query = "gluteo"))) // muscle name, no accent
        assertEquals(listOf("bench_press"), ids(ExerciseFilter(query = "triceps"))) // secondary muscle also matches
    }

    @Test
    fun everyWordMustMatchNameAliasMuscleOrEquipment() {
        val aliased = listOf(bench.copy(exercise = bench.exercise.copy(aliases = listOf("Bench press", "pecho"))), pushUp, squat)
        val find = { q: String -> ExerciseFilter(query = q).apply(aliased, muscles, emptySet()).map { it.exercise.id } }
        assertEquals(listOf("bench_press"), find("bench press")) // English name
        assertEquals(listOf("goblet_squat"), find("sentadilla mancuerna")) // name + equipment word
        assertEquals(listOf("push_up"), find("calistenia pectoral")) // type word + muscle
        assertTrue(find("sentadilla barra").isEmpty()) // all words must match
    }

    @Test
    fun muscleGroupsUsePrimaryMuscles() {
        assertEquals(listOf("bench_press", "push_up"), ids(ExerciseFilter(groups = setOf(com.fitnex.feature.workout.library.MuscleGroup.CHEST))))
        assertEquals(listOf("goblet_squat"), ids(ExerciseFilter(groups = setOf(com.fitnex.feature.workout.library.MuscleGroup.GLUTES))))
        assertTrue(ids(ExerciseFilter(groups = setOf(com.fitnex.feature.workout.library.MuscleGroup.TRICEPS))).isEmpty())
        // Every anatomy-map muscle id belongs to at most one group.
        val all = com.fitnex.feature.workout.library.MuscleGroup.entries.flatMap { it.muscleIds }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun regionUsesPrimaryMusclesOnly() {
        assertEquals(listOf("bench_press", "push_up"), ids(ExerciseFilter(regions = setOf(AnatomicalRegion.CHEST))))
        assertTrue("triceps is only secondary in bench press", ids(ExerciseFilter(regions = setOf(AnatomicalRegion.ARMS))).isEmpty())
    }

    @Test
    fun equipmentChipMeansUsesThatEquipment() {
        assertEquals(listOf("bench_press"), ids(ExerciseFilter(equipment = setOf(Equipment.BENCH))))
    }

    @Test
    fun myEquipmentRequiresEverythingTheExerciseNeeds() {
        val f = ExerciseFilter(onlyMyEquipment = true)
        assertEquals(listOf("push_up"), ids(f)) // nothing at home: body weight only
        assertEquals(listOf("push_up", "goblet_squat"), ids(f, setOf(Equipment.DUMBBELL)))
        assertEquals(listOf("push_up"), ids(f, setOf(Equipment.BARBELL))) // bench press also needs a bench
        assertEquals(3, ids(f, setOf(Equipment.BARBELL, Equipment.BENCH, Equipment.DUMBBELL)).size)
    }

    @Test
    fun favoritesTypeAndCombinations() {
        assertEquals(listOf("push_up"), ids(ExerciseFilter(favoritesOnly = true)))
        assertEquals(listOf("push_up"), ids(ExerciseFilter(types = setOf(ExerciseType.CALISTHENICS))))
        assertTrue(ids(ExerciseFilter(favoritesOnly = true, regions = setOf(AnatomicalRegion.LEGS))).isEmpty())
        assertEquals(2, ExerciseFilter(types = setOf(ExerciseType.STRENGTH), difficulties = setOf(Difficulty.BEGINNER)).sheetFilterCount)
    }
}
