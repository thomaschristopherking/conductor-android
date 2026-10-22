@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.ui.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import app.cash.turbine.test
import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.RecentCache
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.SessionStatus
import build.conductor.android.client.data.settings.SessionSightings
import build.conductor.android.client.data.transcript.Delivery
import build.conductor.android.client.data.transcript.TranscriptItem
import build.conductor.android.client.data.transcript.TranscriptSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class SessionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeConductorRepository()

    private fun TestScope.openSession(isVisible: Boolean = true): SessionViewModel {
        val viewModel = SessionViewModel(repository, FakeConductorRepository.SESSION_ID, "Session", newMessageId = { CLIENT_ID })
        if (isVisible) viewModel.onVisible()
        runCurrent()
        return viewModel
    }

    @Test
    fun `an open question shows while the agent works, and the chosen answers go out as one message`() = runTest {
        repository.sessionStatusValue = "working"
        repository.addQuestion("t1")
        val viewModel = openSession()
        assertEquals("t1", viewModel.uiState.value.openQuestion?.toolUseId)

        viewModel.toggleOption(questionIndex = 0, label = "Green")
        viewModel.toggleOption(questionIndex = 1, label = "Small")
        viewModel.onOtherTextChange(questionIndex = 1, text = "Huge")
        viewModel.sendAnswers()
        runCurrent()

        assertEquals("My answers to your questions:\n1. Which colour?\n   Green\n2. Which sizes?\n   Small, Huge", repository.sentMessages.single().first)
        assertNull(viewModel.uiState.value.openQuestion)
        assertTrue(viewModel.uiState.value.items.last() is TranscriptItem.UserPrompt)
        viewModel.onHidden()
    }

    @Test
    fun `chosen answers survive a poll, and the question closes when its result arrives`() = runTest {
        repository.sessionStatusValue = "working"
        repository.addQuestion("t1")
        val viewModel = openSession()
        viewModel.toggleOption(questionIndex = 0, label = "Red")

        repository.addAgentText("Still thinking")
        advanceTimeBy(3_001)
        assertEquals(setOf("Red"), viewModel.uiState.value.questionAnswers[0].selectedLabels)

        repository.addToolResult("t1", "User responses: 1. Red")
        advanceTimeBy(5_000)
        assertNull(viewModel.uiState.value.openQuestion)
        val card = viewModel.uiState.value.items.filterIsInstance<TranscriptItem.Questions>().single()
        assertEquals("User responses: 1. Red", card.result)
        viewModel.onHidden()
    }

    @Test
    fun `no question is open when the agent does not work`() = runTest {
        repository.addQuestion("t1")

        val viewModel = openSession()

        assertNull(viewModel.uiState.value.openQuestion)
    }

    @Test
    fun `answers with nothing chosen are not sent`() = runTest {
        repository.sessionStatusValue = "working"
        repository.addQuestion("t1")
        val viewModel = openSession()

        viewModel.sendAnswers()
        runCurrent()

        assertTrue(repository.sentMessages.isEmpty())
        assertNotNull(viewModel.uiState.value.openQuestion)
        viewModel.onHidden()
    }

    @Test
    fun `a session that opens again shows its last transcript at once and reads only newer messages`() = runTest {
        repeat(3) { repository.addAgentText("Step $it") }
        val transcripts = RecentCache<String, TranscriptSnapshot>(maxEntries = 4)
        SessionViewModel(repository, FakeConductorRepository.SESSION_ID, "Session", transcripts = transcripts)
        runCurrent()
        repository.addAgentText("Step 3")
        repository.messagesGate = CompletableDeferred()

        val reopened = SessionViewModel(repository, FakeConductorRepository.SESSION_ID, "Session", transcripts = transcripts)
        runCurrent()

        assertEquals(3, reopened.uiState.value.items.size)
        repository.messagesGate?.complete(Unit)
        runCurrent()
        assertEquals(4, reopened.uiState.value.items.size)
        assertEquals(listOf(null, "m3"), repository.messageCursors)
    }

    @Test
    fun `the first load reads the whole transcript and the status`() = runTest {
        repeat(5) { repository.addAgentText("Step $it") }

        val viewModel = openSession()

        val state = viewModel.uiState.value
        assertEquals(listOf<String?>(null), repository.messageCursors)
        assertEquals(5, state.items.size)
        assertEquals(AgentStatus.IDLE, state.status)
        assertEquals("Fix CI", state.title)
        assertFalse(state.isLoading)
        assertFalse(state.isPolling)
    }

    @Test
    fun `while the agent works, each poll asks only for messages after the last one, and polling stops at idle`() = runTest {
        repository.sessionStatusValue = "working"
        repository.addUserMessage("Fix CI", "c0")
        repository.addAgentText("On it")
        val viewModel = openSession()
        assertTrue(viewModel.uiState.value.isPolling)

        repository.addAgentText("Found the bug")
        advanceTimeBy(3_001)
        repository.addTurnEnd()
        repository.sessionStatusValue = "idle"
        advanceTimeBy(3_001)

        assertEquals(listOf(null, "m2", "m3"), repository.messageCursors)
        assertEquals(AgentStatus.IDLE, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isPolling)
        advanceTimeBy(60_000)
        assertEquals(3, repository.messageCursors.size)
    }

    @Test
    fun `quiet polls back off from 3 seconds, and new messages reset the wait`() = runTest {
        repository.sessionStatusValue = "working"
        val viewModel = openSession()

        advanceTimeBy(7_501)
        assertEquals(3, repository.messageCursors.size)
        advanceTimeBy(6_748)
        assertEquals(3, repository.messageCursors.size)
        repository.addAgentText("New")
        advanceTimeBy(2)
        assertEquals(4, repository.messageCursors.size)
        advanceTimeBy(3_000)
        assertEquals(5, repository.messageCursors.size)

        viewModel.onHidden()
    }

    @Test
    fun `a failed poll shows a connection problem and doubles the wait`() = runTest {
        repository.sessionStatusValue = "working"
        val viewModel = openSession()
        repository.messagesFailure = ApiException.Offline(IOException("no network"))

        advanceTimeBy(3_001)
        assertNotNull(viewModel.uiState.value.connectionProblem)
        advanceTimeBy(5_998)
        assertEquals(2, repository.messageCursors.size)
        repository.messagesFailure = null
        advanceTimeBy(2)

        assertEquals(3, repository.messageCursors.size)
        assertNull(viewModel.uiState.value.connectionProblem)
        viewModel.onHidden()
    }

    @Test
    fun `nothing polls while the screen is not visible`() = runTest {
        repository.sessionStatusValue = "working"

        openSession(isVisible = false)
        advanceTimeBy(30_000)

        assertEquals(1, repository.messageCursors.size)
    }

    @Test
    fun `hiding the screen stops polling and showing it again refreshes at once`() = runTest {
        repository.sessionStatusValue = "working"
        val viewModel = openSession()
        advanceTimeBy(3_001)
        assertEquals(2, repository.messageCursors.size)

        viewModel.onHidden()
        advanceTimeBy(30_000)
        assertEquals(2, repository.messageCursors.size)
        assertFalse(viewModel.uiState.value.isPolling)

        viewModel.onVisible()
        runCurrent()
        assertEquals(3, repository.messageCursors.size)
        assertTrue(viewModel.uiState.value.isPolling)
        viewModel.onHidden()
    }

    @Test
    fun `a sent prompt shows at once, polls while the status is still idle, and is not shown twice`() = runTest {
        val viewModel = openSession()
        viewModel.onDraftChange("Run the tests")

        viewModel.send()
        val pending = viewModel.uiState.value.items.single() as TranscriptItem.UserPrompt
        assertEquals(Delivery.SENDING, pending.delivery)
        assertEquals("", viewModel.uiState.value.draft)
        runCurrent()

        assertEquals(listOf("Run the tests" to CLIENT_ID), repository.sentMessages)
        assertEquals(Delivery.QUEUED, (viewModel.uiState.value.items.single() as TranscriptItem.UserPrompt).delivery)
        assertTrue(viewModel.uiState.value.isPolling)
        repository.addUserMessage("Run the tests", CLIENT_ID)
        advanceTimeBy(3_001)
        assertEquals(1, viewModel.uiState.value.items.count { it is TranscriptItem.UserPrompt })
        assertTrue(viewModel.uiState.value.isPolling)

        repository.sessionStatusValue = "working"
        advanceTimeBy(3_001)
        repository.addAgentText("All green")
        repository.addTurnEnd()
        repository.sessionStatusValue = "idle"
        // The poll before brought nothing new, so the wait grew to 4.5 seconds.
        advanceTimeBy(4_501)
        assertFalse(viewModel.uiState.value.isPolling)
        assertEquals(AgentStatus.IDLE, viewModel.uiState.value.status)
    }

    @Test
    fun `a failed send gives the draft back and reports the error`() = runTest {
        repository.sendFailure = ApiException.Server(503, "Conductor is not available")
        val viewModel = openSession()
        viewModel.onDraftChange("Run the tests")

        viewModel.eventFlow.test {
            viewModel.send()
            runCurrent()

            assertEquals(SessionEvent.ShowMessage("Conductor is not available"), awaitItem())
        }
        assertEquals("Run the tests", viewModel.uiState.value.draft)
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun `cancel asks the API to stop and polls until the agent is idle`() = runTest {
        repository.sessionStatusValue = "working"
        val viewModel = openSession()

        viewModel.cancel()
        runCurrent()
        assertEquals(1, repository.cancelCount)
        assertFalse(viewModel.uiState.value.isCancelling)
        assertTrue(viewModel.uiState.value.isPolling)

        repository.sessionStatusValue = "idle"
        advanceTimeBy(3_001)
        assertFalse(viewModel.uiState.value.isPolling)
    }

    @Test
    fun `a rejected key during a poll stops polling`() = runTest {
        repository.sessionStatusValue = "working"
        val viewModel = openSession()
        repository.messagesFailure = ApiException.Unauthorized("Unauthorized client request")

        advanceTimeBy(3_001)
        advanceTimeBy(60_000)

        assertFalse(viewModel.uiState.value.isPolling)
        assertEquals(2, repository.messageCursors.size)
    }

    @Test
    fun `an error status shows the error message`() = runTest {
        repository.sessionStatusValue = "error"

        val viewModel = openSession()

        assertEquals(AgentStatus.ERROR, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isPolling)
    }

    @Test
    fun `the screen records each new status, and a new time of a settled status`() = runTest {
        val sightings = RecordingSightings()
        repository.sessionStatusValue = "working"
        val viewModel = SessionViewModel(repository, FakeConductorRepository.SESSION_ID, "Session", sightings)
        runCurrent()

        viewModel.refresh()
        runCurrent()
        repository.sessionStatusValue = "idle"
        repeat(2) {
            viewModel.refresh()
            runCurrent()
        }
        repository.sessionStatusTimes[FakeConductorRepository.SESSION_ID] = "2026-10-05T07:00:00Z"
        viewModel.refresh()
        runCurrent()

        val time = FakeConductorRepository.DEFAULT_TIME
        assertEquals(listOf("working" to time, "idle" to time, "idle" to "2026-10-05T07:00:00Z"), sightings.statuses)
    }

    @Test
    fun `the screen records the open question that it shows, once`() = runTest {
        val sightings = RecordingSightings()
        repository.sessionStatusValue = "working"
        repository.addQuestion("t1")
        val viewModel = SessionViewModel(repository, FakeConductorRepository.SESSION_ID, "Session", sightings)
        viewModel.onVisible()
        runCurrent()

        repository.addAgentText("Still waiting")
        advanceTimeBy(3_001)

        assertEquals(listOf("t1"), sightings.seenQuestionIds)
        viewModel.onHidden()
    }

    @Test
    fun `each poll reads the status before the messages`() = runTest {
        repository.sessionStatusValue = "working"
        val viewModel = openSession()
        advanceTimeBy(3_001)

        assertEquals(listOf("status", "messages", "status", "messages"), repository.callOrder)
        viewModel.onHidden()
    }

    @Test
    fun `a prompt queued during a turn keeps polling after that turn ends`() = runTest {
        repository.sessionStatusValue = "working"
        val viewModel = openSession()
        viewModel.onDraftChange("Also run lint")
        viewModel.send()
        runCurrent()

        repository.addTurnEnd()
        repository.sessionStatusValue = "idle"
        advanceTimeBy(3_001)

        assertTrue(viewModel.uiState.value.isPolling)
        assertEquals(Delivery.QUEUED, (viewModel.uiState.value.items.last() as TranscriptItem.UserPrompt).delivery)
        repository.addUserMessage("Also run lint", CLIENT_ID)
        repository.sessionStatusValue = "working"
        advanceTimeBy(3_001)
        assertEquals(1, viewModel.uiState.value.items.count { it is TranscriptItem.UserPrompt })
        viewModel.onHidden()
    }

    @Test
    fun `an older prompt with the same text does not hide a new pending prompt`() = runTest {
        repository.addUserMessage("continue", "old")
        repository.sessionStatusValue = "working"
        val viewModel = openSession()
        viewModel.onDraftChange("continue")

        viewModel.send()
        runCurrent()
        advanceTimeBy(3_001)

        assertEquals(2, viewModel.uiState.value.items.count { it is TranscriptItem.UserPrompt })
        viewModel.onHidden()
    }

    @Test
    fun `cancel removes prompts that the server dropped from the queue`() = runTest {
        repository.sessionStatusValue = "working"
        repository.canceledQueuedMessages = 1
        val viewModel = openSession()
        viewModel.onDraftChange("Later")
        viewModel.send()
        runCurrent()

        viewModel.cancel()
        runCurrent()

        assertTrue(viewModel.uiState.value.items.none { it is TranscriptItem.UserPrompt })
        viewModel.onHidden()
    }

    @Test
    fun `a resend of a failed prompt reuses its message id`() = runTest {
        var counter = 0
        val viewModel = SessionViewModel(repository, FakeConductorRepository.SESSION_ID, "Session", newMessageId = { "id-${counter++}" })
        runCurrent()
        repository.sendFailure = ApiException.Offline(IOException("timeout"))
        viewModel.onDraftChange("Run the tests")
        viewModel.send()
        runCurrent()

        repository.sendFailure = null
        viewModel.send()
        runCurrent()

        assertEquals(listOf("Run the tests" to "id-0"), repository.sentMessages)
    }

    private companion object {
        const val CLIENT_ID = "11111111-1111-4111-8111-111111111111"
    }
}

private class RecordingSightings : SessionSightings {
    val statuses = mutableListOf<Pair<String, String>>()
    val seenQuestionIds = mutableListOf<String>()

    override suspend fun recordStatus(status: SessionStatus) {
        statuses += status.status to status.updatedAt
    }

    override suspend fun recordSeenQuestion(sessionId: String, toolUseId: String) {
        seenQuestionIds += toolUseId
    }
}
