package com.fitnex.core.network

import com.fitnex.core.common.result.AppError
import com.fitnex.core.common.result.AppResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SafeApiCallTest {
    private val server = MockWebServer()
    private lateinit var api: FitnexBackendApi

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor({ "test-token" }))
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FitnexBackendApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun successParsesBodyAndSendsBearerToken() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok","apiVersion":"1","extra":true}"""))
        val result = safeApiCall { api.health() }
        assertEquals(AppResult.Success(HealthResponse("ok", "1")), result)
        assertEquals("Bearer test-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun unauthorizedIsTyped() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(AppResult.Failure(AppError.Unauthorized), safeApiCall { api.health() })
    }

    @Test
    fun rateLimitReadsRetryAfter() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
        assertEquals(AppResult.Failure(AppError.RateLimited(30)), safeApiCall { api.health() })
    }

    @Test
    fun malformedBodyIsAServerError() = runTest {
        server.enqueue(MockResponse().setBody("""{"unexpected":1}"""))
        val result = safeApiCall { api.health() }
        assertEquals(AppResult.Failure(AppError.Server(-1, "Invalid response format")), result)
    }

    @Test
    fun noConnectionIsTyped() = runTest {
        server.shutdown()
        assertEquals(AppResult.Failure(AppError.NoConnection), safeApiCall { api.health() })
    }
}
