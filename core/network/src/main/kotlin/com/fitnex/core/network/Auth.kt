package com.fitnex.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Supplies a short-lived identity token (Firebase ID token from phase 3).
 * Returns null when signed out: the request is sent without credentials and the backend rejects it.
 */
fun interface AuthTokenProvider {
    suspend fun currentToken(): String?
}

class AuthInterceptor(
    private val tokenProvider: AuthTokenProvider,
    private val tokenFetcher: (suspend () -> String?) -> String? = { block ->
        kotlinx.coroutines.runBlocking { block() } // OkHttp interceptors run on its own IO threads
    },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenFetcher { tokenProvider.currentToken() }
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(request)
    }
}
