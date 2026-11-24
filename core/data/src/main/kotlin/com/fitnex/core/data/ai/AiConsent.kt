package com.fitnex.core.data.ai

import com.fitnex.core.data.profile.TrainingProfileRules
import com.fitnex.core.data.profile.UserProfileRepository

/**
 * Whether FITNEX AI may be used for [userId]. A "Entrenar juntos" profile is operated by the
 * account owner, who gives (or withdraws) the permission in Ajustes › Cuenta, so the owner's
 * choice counts for it too.
 */
suspend fun UserProfileRepository.hasAiConsent(userId: String): Boolean {
    if (get(userId)?.hasAiConsent == true) return true
    return TrainingProfileRules.isCompanion(userId) && get(TrainingProfileRules.accountOf(userId))?.hasAiConsent == true
}
