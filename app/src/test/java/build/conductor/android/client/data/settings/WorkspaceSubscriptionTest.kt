package build.conductor.android.client.data.settings

import build.conductor.android.client.data.QuestionScan
import build.conductor.android.client.data.api.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceSubscriptionTest {
    private val saved = WorkspaceSubscription(
        "Old name",
        lastActivityAt = "T1",
        sessions = mapOf(
            "s1" to WatchedSession("working", "T1", QuestionScan("m1", null)),
            "s2" to WatchedSession("idle", "T0", seenQuestionId = "t0"),
        ),
    )

    @Test
    fun `a complete observation saves the activity time, the statuses and the scans, and drops a session that is gone`() {
        val observation = WorkspaceObservation(
            "New name",
            lastActivityAt = "T2",
            sessions = mapOf("s1" to SessionObservation(status("s1", "working", "T2"), QuestionScan("m4", openQuestionId = "t1"))),
            isComplete = true,
        )

        val expected = WorkspaceSubscription(
            "New name",
            lastActivityAt = "T2",
            sessions = mapOf("s1" to WatchedSession("working", "T2", QuestionScan("m4", "t1"), seenQuestionId = "t1")),
        )
        assertEquals(expected, saved.withObservation(observation))
    }

    @Test
    fun `an incomplete observation keeps the activity time and the sessions that it did not see`() {
        val observation = WorkspaceObservation(
            "Old name",
            lastActivityAt = "T2",
            sessions = mapOf("s1" to SessionObservation(status("s1", "idle", "T2"))),
            isComplete = false,
        )

        val result = saved.withObservation(observation)

        assertEquals("T1", result.lastActivityAt)
        assertEquals(WatchedSession("idle", "T2", QuestionScan("m1", null)), result.sessions["s1"])
        assertEquals(saved.sessions["s2"], result.sessions["s2"])
    }

    private fun status(sessionId: String, value: String, updatedAt: String) = SessionStatus("w1", sessionId, value, updatedAt)
}
