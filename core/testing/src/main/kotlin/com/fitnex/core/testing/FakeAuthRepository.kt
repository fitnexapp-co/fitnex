package com.fitnex.core.testing

import com.fitnex.core.auth.AuthError
import com.fitnex.core.auth.AuthRepository
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.auth.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory identity provider for tests. Behaves like Firebase for the cases the app relies on:
 * unique e-mails, password check on sign-in and deletion, no account enumeration on reset.
 */
class FakeAuthRepository : AuthRepository {
    private data class Account(val user: AuthUser, val password: String)

    private val accounts = mutableMapOf<String, Account>()
    private val signedIn = MutableStateFlow<AuthUser?>(null)
    private var nextId = 1

    /** When set, every network operation fails with this error (simulates offline, rate limits…). */
    var forcedError: AuthError? = null
    val passwordResetsSent = mutableListOf<String>()
    var verificationEmailsSent = 0
        private set

    override val currentUser: Flow<AuthUser?> = signedIn

    fun signInDirectly(user: AuthUser) {
        signedIn.value = user
    }

    fun markEmailVerified() {
        val current = signedIn.value ?: return
        val verified = current.copy(isEmailVerified = true)
        accounts[current.email.orEmpty()] = accounts.getValue(current.email.orEmpty()).copy(user = verified)
    }

    override suspend fun signUp(email: String, password: String): AuthResult<AuthUser> {
        forcedError?.let { return AuthResult.Failure(it) }
        val key = email.trim().lowercase()
        if (!key.contains('@')) return AuthResult.Failure(AuthError.InvalidEmail)
        if (password.length < 6) return AuthResult.Failure(AuthError.WeakPassword)
        if (key in accounts) return AuthResult.Failure(AuthError.EmailAlreadyInUse)
        val user = AuthUser(uid = "uid-${nextId++}", email = key, isEmailVerified = false)
        accounts[key] = Account(user, password)
        verificationEmailsSent++
        signedIn.value = user
        return AuthResult.Success(user)
    }

    override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> {
        forcedError?.let { return AuthResult.Failure(it) }
        val account = accounts[email.trim().lowercase()]
        if (account == null || account.password != password) return AuthResult.Failure(AuthError.InvalidCredentials)
        signedIn.value = account.user
        return AuthResult.Success(account.user)
    }

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        forcedError?.let { return AuthResult.Failure(it) }
        if (email.trim().lowercase() in accounts) passwordResetsSent += email.trim().lowercase()
        return AuthResult.Success(Unit)
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> {
        forcedError?.let { return AuthResult.Failure(it) }
        if (signedIn.value == null) return AuthResult.Failure(AuthError.NotSignedIn)
        verificationEmailsSent++
        return AuthResult.Success(Unit)
    }

    override suspend fun reload(): AuthResult<AuthUser> {
        forcedError?.let { return AuthResult.Failure(it) }
        val current = signedIn.value ?: return AuthResult.Failure(AuthError.NotSignedIn)
        val fresh = accounts[current.email.orEmpty()]?.user ?: current
        signedIn.value = fresh
        return AuthResult.Success(fresh)
    }

    override suspend fun idToken(forceRefresh: Boolean): String? = signedIn.value?.let { "fake-token-${it.uid}" }

    override suspend fun signOut() {
        signedIn.value = null
    }

    override suspend fun deleteAccount(password: String): AuthResult<Unit> {
        forcedError?.let { return AuthResult.Failure(it) }
        val current = signedIn.value ?: return AuthResult.Failure(AuthError.NotSignedIn)
        val account = accounts.getValue(current.email.orEmpty())
        if (account.password != password) return AuthResult.Failure(AuthError.InvalidCredentials)
        accounts.remove(current.email.orEmpty())
        signedIn.value = null
        return AuthResult.Success(Unit)
    }
}
