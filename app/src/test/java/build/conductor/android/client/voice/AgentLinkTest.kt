package build.conductor.android.client.voice

import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.transcript.TranscriptParser
import build.conductor.android.client.data.transcript.TranscriptSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLinkTest {
    private val repository = FakeConductorRepository()

    @Test
    fun `a saved chat lets the link read only newer messages, and its last reply comes from both`() = runTest {
        repository.addUserMessage("Explain the bug", "c1")
        repository.addAgentText("The cache was stale.")
        val saved = TranscriptSnapshot(TranscriptParser.parse(repository.transcript), "m2", setOf("m1", "m2"))
        repository.addAgentText("I cleared it.")

        val history = AgentLink(repository, FakeConductorRepository.SESSION_ID, saved).start()

        assertEquals(listOf("m2"), repository.messageCursors)
        assertEquals(listOf("The cache was stale.", "I cleared it."), history.lastReply)
        assertEquals(AgentStatus.IDLE, history.status)
    }

    @Test
    fun `after a send the link stays busy while a queued prompt still reports idle`() = runTest {
        val link = AgentLink(repository, FakeConductorRepository.SESSION_ID)
        link.start()
        assertFalse(link.isBusy)

        link.send("Fix it")
        repeat(9) { link.poll() }
        assertTrue(link.isBusy)

        link.poll()
        assertFalse(link.isBusy)
    }
}
