package build.conductor.android.client.notify

import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.settings.StarredSession
import org.junit.Assert.assertEquals
import org.junit.Test

class FinishedSessionsTest {
    @Test
    fun `only a change from working to idle or error is reported`() {
        val starred = mapOf(
            "done" to StarredSession("Done", "working"),
            "failed" to StarredSession("Failed", "working"),
            "still" to StarredSession("Still", "working"),
            "was-idle" to StarredSession("Was idle", "idle"),
            "new" to StarredSession("New", null),
            "unreachable" to StarredSession("Unreachable", "working"),
        )
        val current = mapOf(
            "done" to AgentStatus.IDLE,
            "failed" to AgentStatus.ERROR,
            "still" to AgentStatus.WORKING,
            "was-idle" to AgentStatus.IDLE,
            "new" to AgentStatus.IDLE,
        )

        val finished = findFinishedSessions(starred, current)

        assertEquals(
            listOf(FinishedSession("done", "Done", AgentStatus.IDLE), FinishedSession("failed", "Failed", AgentStatus.ERROR)),
            finished,
        )
    }
}
