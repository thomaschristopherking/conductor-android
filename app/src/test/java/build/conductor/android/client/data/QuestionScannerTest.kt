package build.conductor.android.client.data

import build.conductor.android.client.FakeConductorRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuestionScannerTest {
    private val repository = FakeConductorRepository()
    private val scanner = QuestionScanner(repository)

    @Test
    fun `a scan pages through the transcript and finds the open question`() = runTest {
        repository.addAgentText("One")
        repository.addAgentText("Two")
        repository.addQuestion("t1")

        val scan = scanner.scan(FakeConductorRepository.SESSION_ID, QuestionScan())

        assertEquals(QuestionScan(cursor = "m3", openQuestionId = "t1"), scan)
        assertEquals(listOf(null, "m2"), repository.messageCursors)
    }

    @Test
    fun `a later scan reads only new messages, keeps the question open, and closes it on its result`() = runTest {
        repository.addQuestion("t1")
        val first = scanner.scan(FakeConductorRepository.SESSION_ID, QuestionScan())
        repository.addAgentText("Heartbeat")

        val second = scanner.scan(FakeConductorRepository.SESSION_ID, first)
        assertEquals(QuestionScan(cursor = "m2", openQuestionId = "t1"), second)
        repository.addToolResult("t1", "User responses: 1. Red")

        val third = scanner.scan(FakeConductorRepository.SESSION_ID, second)
        assertNull(third.openQuestionId)
        assertEquals(listOf(null, "m1", "m2"), repository.messageCursors)
    }

    @Test
    fun `the tracker remembers where it stopped for each session`() = runTest {
        repository.addQuestion("t1")
        val tracker = OpenQuestionTracker(scanner)

        assertEquals("t1", tracker.openQuestionId("s1"))
        assertEquals("t1", tracker.openQuestionId("s1"))

        assertEquals(listOf(null, "m1"), repository.messageCursors)
    }
}
