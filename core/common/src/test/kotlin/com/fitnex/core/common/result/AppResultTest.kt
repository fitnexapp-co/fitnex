package com.fitnex.core.common.result

import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {
    @Test
    fun `runCatchingApp wraps success and failure`() {
        assertEquals(AppResult.Success(4), runCatchingApp { 2 + 2 })
        val failure = runCatchingApp { error("boom") }
        assertTrue(failure is AppResult.Failure && failure.error is AppError.Unexpected)
    }

    @Test(expected = CancellationException::class)
    fun `runCatchingApp never swallows coroutine cancellation`() {
        runCatchingApp { throw CancellationException("cancelled") }
    }

    @Test
    fun `asLoadState emits loading then data`() = runTest {
        flowOf(7).asLoadState().test {
            assertEquals(LoadState.Loading, awaitItem())
            assertEquals(LoadState.Ready(7), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `asLoadState turns upstream errors into an error state`() = runTest {
        flow<Int> { throw IllegalStateException("db closed") }.asLoadState().test {
            assertEquals(LoadState.Loading, awaitItem())
            assertTrue(awaitItem() is LoadState.Error)
            awaitComplete()
        }
    }
}
