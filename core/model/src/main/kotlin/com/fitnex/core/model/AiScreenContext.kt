package com.fitnex.core.model

/**
 * Screen FITNEX AI was opened from (spec §19). Phase 8 uses it to attach ONLY the data that
 * screen needs (and the user authorised) to the backend request. [key] is the stable value
 * sent over navigation and to the server; never rename it.
 */
enum class AiScreenContext(val key: String) {
    PROGRESS("progress"),
    NUTRITION("nutrition"),
    WORKOUT("workout"),
    CALENDAR("calendar"),
    STATISTICS("statistics"),
    EXERCISE_LIBRARY("exercise_library"),
    EXERCISE_DETAIL("exercise_detail"),
    MUSCLE_DETAIL("muscle_detail"),
    ;

    companion object {
        fun fromKey(key: String): AiScreenContext? = entries.firstOrNull { it.key == key }
    }
}
