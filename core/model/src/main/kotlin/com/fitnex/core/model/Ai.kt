package com.fitnex.core.model

import java.time.Instant

data class ChatMessage(val id: String, val fromUser: Boolean, val text: String, val at: Instant)

/** One food seen in a photo. Every value is a visual ESTIMATE (spec §6), with the model's confidence 0..1. */
data class DetectedFood(
    val name: String,
    val ingredients: List<String>,
    val grams: Float,
    val portion: String,
    val nutrients: Nutrients,
    val vitamins: List<String>,
    val minerals: List<String>,
    val confidence: Float,
)

/** When the food cannot be identified, [question] asks the user instead of inventing (spec §6). */
data class FoodPhotoAnalysis(
    val identifiable: Boolean,
    val question: String,
    val foods: List<DetectedFood>,
    val notes: String,
)

/** What the user wants from an AI-built routine. */
enum class RoutineGoal { STRENGTH, HYPERTROPHY, ENDURANCE, GENERAL }

data class RoutineRequest(
    val goal: RoutineGoal,
    val sessionMinutes: Int,
    /** Free text such as "me molesta la rodilla" (sent only if the user wrote it). */
    val focus: String = "",
)

/** One exercise chosen by FITNEX AI, always from the app's own catalogue. */
data class ProposedExercise(
    val exerciseId: String,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val note: String,
)

/** A plan the user reviews before saving; nothing is stored until they accept it. */
data class RoutineProposal(
    val name: String,
    val rationale: String,
    val exercises: List<ProposedExercise>,
)

/** A weekly plan: one proposal per training day, saved as separate routines. */
data class RoutinePlanProposal(
    val name: String,
    val rationale: String,
    val sessions: List<RoutineProposal>,
)

/** Why an AI request could not be answered, in terms the UI can explain. */
enum class AiFailure { NO_CONSENT, NOT_SIGNED_IN, NO_CONNECTION, RATE_LIMITED, NOT_CONFIGURED, UNAVAILABLE }
