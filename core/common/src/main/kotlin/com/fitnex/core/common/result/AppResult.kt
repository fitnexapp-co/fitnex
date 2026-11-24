package com.fitnex.core.common.result

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Typed error categories so the UI can show a precise, recoverable message. */
sealed interface AppError {
    data object NoConnection : AppError
    data object Unauthorized : AppError
    data object NotFound : AppError
    data class RateLimited(val retryAfterSeconds: Long?) : AppError
    data class Server(val code: Int, val message: String?) : AppError
    data class Validation(val field: String, val reason: String) : AppError
    data class Unexpected(val cause: Throwable) : AppError
}

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** UI-facing tri-state used by StateFlow-backed screens. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
    data class Error(val error: AppError) : LoadState<Nothing>
}

inline fun <T> runCatchingApp(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e // never swallow coroutine cancellation
} catch (e: Throwable) {
    AppResult.Failure(AppError.Unexpected(e))
}

fun <T> Flow<T>.asLoadState(): Flow<LoadState<T>> =
    map<T, LoadState<T>> { LoadState.Ready(it) }
        .onStart { emit(LoadState.Loading) }
        .catch { emit(LoadState.Error(AppError.Unexpected(it))) }
