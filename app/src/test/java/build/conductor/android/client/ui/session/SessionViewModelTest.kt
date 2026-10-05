package build.conductor.android.client.ui.session

import app.cash.turbine.test
import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.settings.StarredSessions
import build.conductor.android.client.data.transcript.Delivery
import build.conductor.android.client.data.transcript.TranscriptItem
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
    fun `the first load pages through the whole transcript and reads the status`() = runTest {
        repeat(5) { repository.addAgentText("Step $it") }

        val viewModel = openSession()

        val state = viewModel.uiState.value
        assertEquals(listOf(null, "m2", "m4"), repository.messageCursors)
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
    fun `the star follows the starred sessions store`() = runTest {
        val starred = object : StarredSessions {
            override val starredIds = MutableStateFlow(emptySet<String>())
            override suspend fun setStarred(sessionId: String, title: String, isStarred: Boolean) {
                starredIds.value = if (isStarred) setOf(sessionId) else emptySet()
            }
        }
        val viewModel = SessionViewModel(repository, FakeConductorRepository.SESSION_ID, "Session", starred)
        runCurrent()

        viewModel.toggleStar()
        runCurrent()

        assertTrue(viewModel.uiState.value.isStarred)
        assertEquals(setOf(FakeConductorRepository.SESSION_ID), starred.starredIds.value)
    }

    private companion object {
        const val CLIENT_ID = "11111111-1111-4111-8111-111111111111"
    }
}
