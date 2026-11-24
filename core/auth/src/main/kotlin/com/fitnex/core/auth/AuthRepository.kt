package com.fitnex.core.auth

import kotlinx.coroutines.flow.Flow

/** The signed-in identity. [uid] is the stable key of every user-owned row in Room and Firestore. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val isEmailVerified: Boolean,
)

/**
 * Authentication failures the UI can explain precisely. Sign-in deliberately does not
 * distinguish "unknown email" from "wrong password" ([InvalidCredentials]) so the screen never
 * reveals whether an address has an account (email-enumeration protection).
 */
sealed interface AuthError {
    data object InvalidEmail : AuthError
    data object WeakPassword : AuthError
    data object EmailAlreadyInUse : AuthError
    data object InvalidCredentials : AuthError
    data object UserDisabled : AuthError
    data object TooManyRequests : AuthError
    data object Network : AuthError
    /** Sensitive operation (account deletion) needs a fresh sign-in. */
    data object RequiresRecentLogin : AuthError
    data object NotSignedIn : AuthError
    data class Unknown(val message: String?) : AuthError
}

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val error: AuthError) : AuthResult<Nothing>
}

interface AuthRepository {
    /** Emits on sign-in, sign-out and verification changes; null when signed out. */
    val currentUser: Flow<AuthUser?>

    suspend fun signUp(email: String, password: String): AuthResult<AuthUser>
    suspend fun signIn(email: String, password: String): AuthResult<AuthUser>

    /** Always reports success for unknown addresses too (no account enumeration). */
    suspend fun sendPasswordReset(email: String): AuthResult<Unit>
    suspend fun sendEmailVerification(): AuthResult<Unit>

    /** Refreshes the user from the server, e.g. after they tapped the verification link. */
    suspend fun reload(): AuthResult<AuthUser>

    /** Short-lived ID token for the FITNEX backend; null when signed out. */
    suspend fun idToken(forceRefresh: Boolean = false): String?

    suspend fun signOut()

    /** Re-authenticates with [password] and permanently deletes the account. */
    suspend fun deleteAccount(password: String): AuthResult<Unit>
}
