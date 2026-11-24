package com.fitnex.core.data.session

import com.fitnex.core.auth.AuthError
import com.fitnex.core.auth.AuthRepository
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.auth.AuthUser
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.sync.CloudSync
import com.fitnex.core.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Which part of the app the user may see. Drives the root navigation. */
sealed interface SessionState {
    data object SignedOut : SessionState
    /** Signed in on a device without this user's data: the cloud copy is being downloaded. */
    data class Restoring(val user: AuthUser) : SessionState
    data class NeedsOnboarding(val user: AuthUser, val profile: UserProfile?) : SessionState
    data class Ready(val user: AuthUser, val profile: UserProfile) : SessionState
}

/** Which "Entrenar juntos" profile of an account is on screen (null = the account owner). */
interface ActiveProfileSource {
    fun active(accountId: String): Flow<String?>

    object None : ActiveProfileSource {
        override fun active(accountId: String): Flow<String?> = flowOf(null)
    }
}

@Singleton
class SessionRepository @Inject constructor(
    private val auth: AuthRepository,
    private val profiles: UserProfileRepository,
    private val sync: CloudSync,
    private val activeProfiles: ActiveProfileSource = ActiveProfileSource.None,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<SessionState> = auth.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(SessionState.SignedOut)
            } else {
                combine(profiles.observe(user.uid), sync.restoringUserId) { profile, restoring ->
                    if (profile == null && restoring == user.uid) {
                        SessionState.Restoring(user)
                    } else if (profile != null && profile.hasCompletedOnboarding) {
                        SessionState.Ready(user, profile)
                    } else {
                        SessionState.NeedsOnboarding(user, profile)
                    }
                }
            }
        }
        .distinctUntilChanged()

    /** The signed-in account (identity, cloud sync, account deletion). */
    val accountUserId: Flow<String?> = auth.currentUser.map { it?.uid }.distinctUntilChanged()

    /**
     * Whose data is on screen, for repositories that scope data per user: the account owner, or the
     * "Entrenar juntos" profile chosen in the account menu ("<uid>~…", same account, own data).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUserId: Flow<String?> = accountUserId
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else activeProfiles.active(uid).map { it ?: uid } }
        .distinctUntilChanged()
}

/** Account-level operations that touch both the identity provider and local data. */
@Singleton
class AccountManager @Inject constructor(
    private val auth: AuthRepository,
    private val profiles: UserProfileRepository,
    private val sync: CloudSync,
) {
    /**
     * Local data is kept on sign-out (it is scoped by user id; pending changes upload at the next
     * sign-in of that user); nothing from one account is ever shown to another.
     */
    suspend fun signOut() = auth.signOut()

    /**
     * Deletes the remote account FIRST; local data is wiped only once that succeeded, so a failed
     * deletion (wrong password, no network) never leaves the user with an account but no data;
     * if the cloud copy was already erased by then, it is uploaded again.
     */
    suspend fun deleteAccount(userId: String, password: String): AuthResult<Unit> {
        if (password.isBlank()) return AuthResult.Failure(AuthError.InvalidCredentials)
        // The cloud copy goes first: once the Firebase account is gone its token can no longer erase it.
        if (!sync.eraseCloudCopy(userId)) return AuthResult.Failure(AuthError.Network)
        val result = auth.deleteAccount(password)
        if (result is AuthResult.Success) profiles.deleteAllData(userId) else sync.reuploadAll(userId)
        return result
    }
}
