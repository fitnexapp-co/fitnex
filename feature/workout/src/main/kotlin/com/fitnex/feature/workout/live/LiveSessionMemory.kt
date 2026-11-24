package com.fitnex.feature.workout.live

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the live workout keeps outside Room while the app is open: the rest countdown, the values
 * edited for the next set and the extra sets added with "+ Serie". It outlives the screen, so
 * leaving the workout (X, the mini player, another tab) and coming back finds them as they were.
 * It belongs to one session: a different session starts from scratch.
 */
@Singleton
class LiveSessionMemory @Inject constructor() {
    val rest = MutableStateFlow<RestTimer?>(null)
    val drafts = MutableStateFlow<Map<String, SetDraft>>(emptyMap())
    val extraSets = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var sessionId: String? = null

    /** Keeps the values when [id] is the session they belong to; otherwise starts empty. */
    @Synchronized
    fun bind(id: String) {
        if (id != sessionId) {
            clear()
            sessionId = id
        }
    }

    @Synchronized
    fun clear() {
        sessionId = null
        rest.value = null
        drafts.value = emptyMap()
        extraSets.value = emptyMap()
    }
}
