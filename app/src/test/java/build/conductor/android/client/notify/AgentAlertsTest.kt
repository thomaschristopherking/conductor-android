package build.conductor.android.client.notify

import build.conductor.android.client.data.QuestionScan
import build.conductor.android.client.data.api.SessionStatus
import build.conductor.android.client.data.settings.SessionObservation
import build.conductor.android.client.data.settings.WatchedSession
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentAlertsTest {
    @Test
    fun `a turn that ended since the last check is reported, also when the check did not see it start`() {
        val saved = mapOf(
            "done" to WatchedSession("working", "T1"),
            "failed" to WatchedSession("working", "T1"),
            "still" to WatchedSession("working", "T1"),
            "quiet" to WatchedSession("idle", "T1"),
            "missed" to WatchedSession("idle", "T1"),
            "no-time" to WatchedSession("idle"),
        )
        val observed = mapOf(
            "done" to seen("done", "idle", "T2"),
            "failed" to seen("failed", "error", "T2"),
            "still" to seen("still", "working", "T2"),
            "quiet" to seen("quiet", "idle", "T1"),
            "missed" to seen("missed", "idle", "T2"),
            "no-time" to seen("no-time", "idle", "T2"),
            "new" to seen("new", "idle", "T2"),
        )

        val expected = mapOf("done" to AgentAlert.FINISHED, "failed" to AgentAlert.FAILED, "missed" to AgentAlert.FINISHED)
        assertEquals(expected, findAlerts(saved, observed))
    }

    @Test
    fun `a question is reported once, and not when the screen already showed it`() {
        val saved = mapOf(
            "new" to WatchedSession("working", "T1"),
            "told" to WatchedSession("working", "T1", seenQuestionId = "t2"),
            "none" to WatchedSession("working", "T1"),
        )
        val observed = mapOf(
            "new" to seen("new", "working", "T2", QuestionScan("m9", openQuestionId = "t1")),
            "told" to seen("told", "working", "T2", QuestionScan("m4", openQuestionId = "t2")),
            "none" to seen("none", "working", "T2", QuestionScan("m2", openQuestionId = null)),
        )

        assertEquals(mapOf("new" to AgentAlert.ASKED), findAlerts(saved, observed))
    }

    private fun seen(sessionId: String, status: String, updatedAt: String, scan: QuestionScan? = null) =
        SessionObservation(SessionStatus("w1", sessionId, status, updatedAt), scan)
}
