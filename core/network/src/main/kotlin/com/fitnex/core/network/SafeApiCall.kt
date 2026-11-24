package com.fitnex.core.network

import com.fitnex.core.common.result.AppError
import com.fitnex.core.common.result.AppResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** Maps transport failures to typed [AppError]s so screens can show a precise, recoverable state. */
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    AppResult.Failure(e.toAppError())
} catch (e: IOException) {
    AppResult.Failure(AppError.NoConnection)
} catch (e: SerializationException) {
    // The backend validates Gemini output, so a malformed body is a server contract bug.
    AppResult.Failure(AppError.Server(code = -1, message = "Invalid response format"))
} catch (e: RuntimeException) {
    // Anything else (a misconfigured converter, a bad URL…) is a failed call, never an app crash:
    // most calls run in background sync where an uncaught exception would kill the process.
    AppResult.Failure(AppError.Server(code = -1, message = e.message ?: e.javaClass.simpleName))
}

internal fun HttpException.toAppError(): AppError = when (code()) {
    401, 403 -> AppError.Unauthorized
    404 -> AppError.NotFound
    429 -> AppError.RateLimited(response()?.headers()?.get("Retry-After")?.toLongOrNull())
    else -> AppError.Server(code(), message())
}
