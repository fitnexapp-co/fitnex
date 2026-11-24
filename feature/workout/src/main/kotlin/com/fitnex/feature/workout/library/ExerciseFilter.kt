package com.fitnex.feature.workout.library

import com.fitnex.core.model.AnatomicalRegion
import com.fitnex.core.model.Difficulty
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.ExerciseListItem
import com.fitnex.core.model.ExerciseType
import com.fitnex.core.model.Muscle
import java.text.Normalizer

/**
 * Muscle groups shown as quick filters. They map onto the anatomy-map muscles (same ids as the
 * 2D/3D maps); an exercise belongs to a group when one of its PRIMARY muscles is in it.
 */
enum class MuscleGroup(val muscleIds: Set<String>) {
    CHEST(setOf("pectoralis_major")),
    BACK(setOf("latissimus_dorsi", "rhomboids")),
    SHOULDERS(setOf("deltoid_anterior", "deltoid_lateral", "deltoid_posterior")),
    BICEPS(setOf("biceps_brachii")),
    TRICEPS(setOf("triceps_brachii")),
    FOREARMS(setOf("forearm")),
    QUADS(setOf("quadriceps")),
    HAMSTRINGS(setOf("hamstrings")),
    GLUTES(setOf("gluteus_maximus")),
    ADDUCTORS(setOf("adductors")),
    ABDUCTORS(setOf("gluteus_medius")),
    CALVES(setOf("calves")),
    ABS(setOf("rectus_abdominis", "obliques")),
    TRAPS(setOf("trapezius")),
    LOWER_BACK(setOf("erector_spinae")),
}

/**
 * Library filters (spec §7). Empty sets mean "no restriction".
 * - [equipment]: exercises that USE any of the chosen equipment.
 * - [onlyMyEquipment]: exercises whose required equipment is ALL in the user's profile.
 */
data class ExerciseFilter(
    val query: String = "",
    val regions: Set<AnatomicalRegion> = emptySet(),
    val groups: Set<MuscleGroup> = emptySet(),
    val equipment: Set<Equipment> = emptySet(),
    val types: Set<ExerciseType> = emptySet(),
    val difficulties: Set<Difficulty> = emptySet(),
    val favoritesOnly: Boolean = false,
    val onlyMyEquipment: Boolean = false,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || regions.isNotEmpty() || groups.isNotEmpty() || equipment.isNotEmpty() || types.isNotEmpty() ||
            difficulties.isNotEmpty() || favoritesOnly || onlyMyEquipment

    /** Number of filters hidden in the "Filtros" sheet, for its badge. */
    val sheetFilterCount: Int get() = equipment.size + types.size + difficulties.size

    fun apply(items: List<ExerciseListItem>, muscles: Map<String, Muscle>, myEquipment: Set<Equipment>): List<ExerciseListItem> {
        val words = tokens(query)
        val groupIds = groups.flatMap { it.muscleIds }.toSet()
        return items.filter { item ->
            val e = item.exercise
            (words.isEmpty() || matchesQuery(item, muscles, words)) &&
                (regions.isEmpty() || e.primaryMuscleIds.any { muscles[it]?.region in regions }) &&
                (groupIds.isEmpty() || e.primaryMuscleIds.any { it in groupIds }) &&
                (equipment.isEmpty() || e.equipment.any { it in equipment }) &&
                (types.isEmpty() || e.type in types) &&
                (difficulties.isEmpty() || e.difficulty in difficulties) &&
                (!favoritesOnly || item.isFavorite) &&
                (!onlyMyEquipment || myEquipment.containsAll(e.requiredEquipment))
        }.let { found -> if (words.isEmpty()) found else found.sortedBy { rank(it, words) } }
    }

    /**
     * Every word typed must appear somewhere: name, English or common name, tags, muscles or
     * equipment ("press inclinado mancuerna", "curl ez", "lat pulldown", "polea triceps").
     */
    private fun matchesQuery(item: ExerciseListItem, muscles: Map<String, Muscle>, words: List<String>): Boolean {
        val haystack = searchText(item, muscles)
        return words.all { w -> haystack.contains(w) || (w.length > 3 && haystack.contains(w.dropLast(1))) }
    }

    /**
     * Name matches first ("plancha abdominal" → Plancha before Bird dog), then the rest; the more
     * typed words the name holds, the higher. Alphabetical order is kept within each tier.
     */
    private fun rank(item: ExerciseListItem, words: List<String>): Int {
        val name = normalize(item.exercise.name)
        if (name.startsWith(words.joinToString(" "))) return 0
        val inName = words.count { name.contains(it) }
        return if (inName == 0) words.size + 1 else 1 + words.size - inName
    }

    private fun searchText(item: ExerciseListItem, muscles: Map<String, Muscle>): String {
        val e = item.exercise
        return buildString {
            append(normalize(e.name)).append(' ')
            e.aliases.forEach { append(normalize(it)).append(' ') }
            e.muscles.forEach { m -> muscles[m.muscleId]?.name?.let { append(normalize(it)).append(' ') } }
            e.equipment.forEach { append(EQUIPMENT_WORDS[it].orEmpty()).append(' ') }
            append(TYPE_WORDS[e.type].orEmpty())
        }
    }

    companion object {
        /** Lower-case without accents: "Glúteo" matches "gluteo". */
        fun normalize(text: String): String =
            Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

        private fun tokens(query: String): List<String> =
            normalize(query).split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }

        /** Spanish and English words people type for each piece of equipment. */
        private val EQUIPMENT_WORDS = mapOf(
            Equipment.BODYWEIGHT to "peso corporal sin material bodyweight calistenia",
            Equipment.DUMBBELL to "mancuerna mancuernas dumbbell",
            Equipment.BARBELL to "barra barbell",
            Equipment.KETTLEBELL to "kettlebell pesa rusa",
            Equipment.MACHINE to "maquina machine multipower smith",
            Equipment.CABLE to "polea poleas cable",
            Equipment.RESISTANCE_BAND to "banda goma elastica band",
            Equipment.BENCH to "banco bench",
            Equipment.PULL_UP_BAR to "barra de dominadas pull up bar",
            Equipment.OTHER to "accesorio",
            Equipment.EZ_BAR to "barra z ez",
            Equipment.DIP_BARS to "paralelas fondos dip",
            Equipment.RACK to "rack jaula",
            Equipment.MEDICINE_BALL to "balon medicinal medicine ball",
            Equipment.ROPE to "cuerda comba rope",
            Equipment.SLED to "trineo sled",
            Equipment.SANDBAG to "saco sandbag",
            Equipment.CARDIO_MACHINE to "maquina cardio",
        )

        private val TYPE_WORDS = mapOf(
            ExerciseType.CARDIO to "cardio",
            ExerciseType.MOBILITY to "movilidad mobility",
            ExerciseType.STRETCHING to "estiramiento stretch",
            ExerciseType.FUNCTIONAL to "funcional cuerpo completo",
            ExerciseType.CALISTHENICS to "calistenia",
        )
    }
}
