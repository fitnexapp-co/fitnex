package com.fitnex.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.auth.AuthError
import com.fitnex.core.auth.AuthRepository
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.session.AccountManager
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountInfo(
    val userId: String,
    val name: String,
    val email: String?,
    val emailVerified: Boolean,
    val aiConsent: Boolean,
)

enum class VerificationNotice { SENT, STILL_UNVERIFIED, VERIFIED }

data class AccountActionsState(
    val verificationNotice: VerificationNotice? = null,
    val verificationError: AuthError? = null,
    val deleting: Boolean = false,
    val deleteError: AuthError? = null,
    val resetting: Boolean = false,
    val resetDone: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    session: SessionRepository,
    private val auth: AuthRepository,
    private val profiles: UserProfileRepository,
    private val accounts: AccountManager,
    private val photos: com.fitnex.core.datastore.ProfilePhotoStore,
    private val trainingReset: com.fitnex.core.data.training.TrainingResetRepository,
    private val trainingProfiles: com.fitnex.core.data.profile.TrainingProfilesRepository,
) : ViewModel() {

    /**
     * A friend's "Entrenar juntos" profile is active: account-wide actions (sign out, reset every
     * profile's history, delete the account) stay with the account holder.
     */
    val companionActive: StateFlow<Boolean> = session.currentUserId
        .map { it != null && com.fitnex.core.data.profile.TrainingProfileRules.isCompanion(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Back to the account holder's own profile. */
    fun switchToOwner() = viewModelScope.launch {
        account.value?.userId?.let { trainingProfiles.switchTo(it) }
    }

    val account: StateFlow<AccountInfo?> = session.state
        .map { s ->
            (s as? SessionState.Ready)?.let {
                AccountInfo(it.user.uid, it.profile.name, it.user.email, it.user.isEmailVerified, it.profile.hasAiConsent)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _actions = MutableStateFlow(AccountActionsState())
    val actions: StateFlow<AccountActionsState> = _actions.asStateFlow()

    fun resendVerification() = viewModelScope.launch {
        when (val r = auth.sendEmailVerification()) {
            is AuthResult.Success -> _actions.update { it.copy(verificationNotice = VerificationNotice.SENT, verificationError = null) }
            is AuthResult.Failure -> _actions.update { it.copy(verificationError = r.error) }
        }
    }

    /** The user tapped the link in the e-mail: ask Firebase for the fresh state. */
    fun checkVerification() = viewModelScope.launch {
        when (val r = auth.reload()) {
            is AuthResult.Success -> _actions.update {
                it.copy(
                    verificationNotice = if (r.value.isEmailVerified) VerificationNotice.VERIFIED else VerificationNotice.STILL_UNVERIFIED,
                    verificationError = null,
                )
            }
            is AuthResult.Failure -> _actions.update { it.copy(verificationError = r.error) }
        }
    }

    fun setAiConsent(granted: Boolean) {
        val uid = account.value?.userId ?: return
        viewModelScope.launch { profiles.setAiConsent(uid, granted) }
    }

    fun signOut() = viewModelScope.launch { accounts.signOut() }

    /** Erases the training history of the account and every profile ("empezar de 0"). */
    fun resetTrainingStats() {
        val uid = account.value?.userId ?: return
        if (_actions.value.resetting) return
        _actions.update { it.copy(resetting = true, resetDone = false) }
        viewModelScope.launch {
            trainingReset.resetAll(uid)
            _actions.update { it.copy(resetting = false, resetDone = true) }
        }
    }

    fun clearDeleteError() = _actions.update { it.copy(deleteError = null) }

    /** On success the session becomes SignedOut and the app returns to the welcome screen. */
    fun deleteAccount(password: String) {
        val uid = account.value?.userId ?: return
        if (_actions.value.deleting) return
        _actions.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            val r = accounts.deleteAccount(uid, password)
            if (r is AuthResult.Success) photos.remove(uid) // the photo lives only on this device
            _actions.update { it.copy(deleting = false, deleteError = (r as? AuthResult.Failure)?.error) }
        }
    }
}
