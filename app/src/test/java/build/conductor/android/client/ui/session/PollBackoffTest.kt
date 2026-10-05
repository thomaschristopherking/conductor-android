package build.conductor.android.client.ui.session

import org.junit.Assert.assertEquals
import org.junit.Test

class PollBackoffTest {
    @Test
    fun `quiet polls grow by half up to 15 seconds`() {
        val backoff = PollBackoff()

        val waits = (1..6).map { backoff.onNoNewMessages(); backoff.currentMillis }

        assertEquals(listOf(4_500L, 6_750L, 10_125L, 15_000L, 15_000L, 15_000L), waits)
    }

    @Test
    fun `failures double up to 60 seconds and new messages reset to 3 seconds`() {
        val backoff = PollBackoff()

        val waits = (1..6).map { backoff.onFailure(); backoff.currentMillis }
        backoff.onNewMessages()

        assertEquals(listOf(6_000L, 12_000L, 24_000L, 48_000L, 60_000L, 60_000L), waits)
        assertEquals(3_000L, backoff.currentMillis)
    }

    @Test
    fun `a quiet poll after failures returns to the quiet limit`() {
        val backoff = PollBackoff()
        repeat(4) { backoff.onFailure() }

        backoff.onNoNewMessages()

        assertEquals(15_000L, backoff.currentMillis)
    }
}
