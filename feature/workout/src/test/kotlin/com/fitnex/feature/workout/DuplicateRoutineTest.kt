package com.fitnex.feature.workout

import com.fitnex.core.model.RoutineExercise
import com.fitnex.core.model.RoutineLimits
import com.fitnex.feature.workout.editor.RoutineEditorUiState
import com.fitnex.feature.workout.editor.duplicateOf
import java.time.DayOfWeek
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateRoutineTest {
    private val items = listOf(
        RoutineExercise("a", "bench_press", "Press de banca", 4, 8, 120, 60f),
        RoutineExercise("b", "push_up", "Flexiones", 3, 12, 60, null),
    )

    @Test
    fun copyKeepsExercisesWithNewIdsAndNoWeekdays() {
        var n = 0
        val state = RoutineEditorUiState(loading = false, isNew = false, name = "Empuje", days = setOf(DayOfWeek.MONDAY), isTemplate = true, items = items)
        val copy = duplicateOf(state, " (copia)", "new", Instant.EPOCH) { "id-${n++}" }

        assertEquals("new", copy.id)
        assertEquals("Empuje (copia)", copy.name)
        assertTrue("not scheduled twice", copy.days.isEmpty())
        assertFalse(copy.isTemplate)
        assertEquals(items.map { it.copy(id = "") }, copy.exercises.map { it.copy(id = "") })
        assertTrue("fresh ids", copy.exercises.none { it.id == "a" || it.id == "b" })
    }

    @Test
    fun longNamesStayWithinTheLimit() {
        val state = RoutineEditorUiState(loading = false, isNew = false, name = "x".repeat(RoutineLimits.NAME_MAX_LENGTH), items = items)
        val copy = duplicateOf(state, " (copia)", "new", Instant.EPOCH) { "id" }
        assertTrue(copy.name.length <= RoutineLimits.NAME_MAX_LENGTH)
        assertTrue(copy.name.endsWith(" (copia)"))
    }
}
