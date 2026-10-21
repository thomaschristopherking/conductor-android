package build.conductor.android.client.data.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HedgePolicyTest {
    private val policy = HedgePolicy(delayMillis = 1_000, maxCopies = 3)

    @Test
    fun `a fast answer sends one copy only`() = runTest {
        var copies = 0

        val answer = policy.hedge {
            copies++
            delay(200)
            "fast"
        }

        assertEquals("fast", answer)
        assertEquals(1, copies)
    }

    @Test
    fun `a slow answer starts a new copy each second, and the first answer wins`() = runTest {
        val copyDurations = ArrayDeque(listOf(6_000L, 5_000L, 300L))
        var copies = 0

        val answer = policy.hedge {
            copies++
            delay(copyDurations.removeFirst())
            "copy $copies"
        }

        assertEquals("copy 3", answer)
        assertEquals(3, copies)
        assertEquals(2_300, currentTime)
    }

    @Test
    fun `the first answer cancels the copies that still wait`() = runTest {
        val cancelled = mutableListOf<Int>()
        var copies = 0

        policy.hedge {
            val copy = ++copies
            if (copy == 2) return@hedge "second"
            try {
                awaitCancellation()
            } finally {
                cancelled += copy
            }
        }

        assertEquals(listOf(1), cancelled)
        assertEquals(2, copies)
    }

    @Test
    fun `a failure is an answer, so it does not wait for the other copies`() = runTest {
        val failure = runCatching<String> {
            policy.hedge {
                delay(1_500)
                throw IOException("offline")
            }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(1_500, currentTime)
    }

    @Test
    fun `no more than the maximum number of copies start`() = runTest {
        var copies = 0
        val gate = CompletableDeferred<String>()
        val answer = async { policy.hedge { copies++; gate.await() } }

        advanceTimeBy(60_000)
        runCurrent()
        gate.complete("late")

        assertEquals("late", answer.await())
        assertEquals(3, copies)
    }
}
