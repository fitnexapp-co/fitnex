package com.fitnex.core.auth.firebase

import com.fitnex.core.auth.AuthError
import com.fitnex.core.auth.AuthRepository
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.auth.AuthUser
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthRepository {

    /** Bumped after reload() so verification changes are emitted (Firebase has no listener for it). */
    private val refreshTick = MutableStateFlow(0)

    private val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val currentUser: Flow<AuthUser?> =
        combine(authState, refreshTick) { user, _ -> user?.toAuthUser() }.distinctUntilChanged()

    override suspend fun signUp(email: String, password: String) = call {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("No user returned")
        // Best effort: the account exists even if the verification mail fails to send.
        runCatching { user.sendEmailVerification().await() }
        user.toAuthUser()
    }

    override suspend fun signIn(email: String, password: String) = call {
        val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("No user returned")
        user.toAuthUser()
    }

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        val result = call { auth.sendPasswordResetEmail(email.trim()).await(); Unit }
        // Never reveal whether the address is registered.
        return if (result is AuthResult.Failure && result.error == AuthError.InvalidCredentials) {
            AuthResult.Success(Unit)
        } else {
            result
        }
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> = call {
        val user = auth.currentUser ?: return AuthResult.Failure(AuthError.NotSignedIn)
        user.sendEmailVerification().await()
        Unit
    }

    override suspend fun reload(): AuthResult<AuthUser> = call {
        val user = auth.currentUser ?: return AuthResult.Failure(AuthError.NotSignedIn)
        user.reload().await()
        refreshTick.value++
        (auth.currentUser ?: user).toAuthUser()
    }

    override suspend fun idToken(forceRefresh: Boolean): String? =
        runCatching { auth.currentUser?.getIdToken(forceRefresh)?.await()?.token }.getOrNull()

    override suspend fun signOut() = auth.signOut()

    override suspend fun deleteAccount(password: String): AuthResult<Unit> = call {
        val user = auth.currentUser ?: return AuthResult.Failure(AuthError.NotSignedIn)
        val email = user.email ?: return AuthResult.Failure(AuthError.NotSignedIn)
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        user.delete().await()
        Unit
    }

    private inline fun <T> call(block: () -> T): AuthResult<T> = try {
        AuthResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Failure(mapFirebaseError(e))
    }
}

/** Translates Firebase exceptions into [AuthError]. Order matters: subclasses first. */
internal fun mapFirebaseError(e: Exception): AuthError = when (e) {
    is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword
    is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse
    is FirebaseAuthRecentLoginRequiredException -> AuthError.RequiresRecentLogin
    is FirebaseAuthInvalidUserException ->
        if (e.errorCode == "ERROR_USER_DISABLED") AuthError.UserDisabled else AuthError.InvalidCredentials
    is FirebaseAuthInvalidCredentialsException ->
        if (e.errorCode == "ERROR_INVALID_EMAIL") AuthError.InvalidEmail else AuthError.InvalidCredentials
    is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
    is FirebaseNetworkException -> AuthError.Network
    else -> AuthError.Unknown(e.message)
}

private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email, isEmailVerified = isEmailVerified)
