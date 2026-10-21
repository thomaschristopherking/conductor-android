@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.ui.workspace

import kotlinx.coroutines.ExperimentalCoroutinesApi
import app.cash.turbine.test
import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ModelSelection
import build.conductor.android.client.data.OpenQuestionTracker
import build.conductor.android.client.data.QuestionScanner
import build.conductor.android.client.data.api.Session
import build.conductor.android.client.ui.components.LoadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkspaceDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeConductorRepository().apply {
        sessionsInWorkspace = listOf(Session("s1", "conductor://1", "One"), Session("s2", "conductor://2", "Two"))
        sessionStatuses = mutableMapOf("s1" to "working", "s2" to "error")
    }

    @Test
    fun `the detail shows each session with its own status`() = runTest {
        val viewModel = WorkspaceDetailViewModel(repository, "w1", OpenQuestionTracker(QuestionScanner(repository)))
        advanceUntilIdle()

        val detail = (viewModel.uiState.value.detail as LoadState.Loaded).value
        assertEquals(listOf(AgentStatus.WORKING, AgentStatus.ERROR), detail.sessions.map { it.status })
        assertEquals("Workspace w1", detail.workspace.name)
    }

    @Test
    fun `a working session in another tab shows its open question, and only working sessions are scanned`() = runTest {
        repository.addQuestion("t1")
        val viewModel = WorkspaceDetailViewModel(repository, "w1", OpenQuestionTracker(QuestionScanner(repository)), initialSessionId = "s2")
        advanceUntilIdle()

        val detail = (viewModel.uiState.value.detail as LoadState.Loaded).value
        assertEquals(listOf(true, false), detail.sessions.map { it.hasOpenQuestion })
        assertEquals(listOf<String?>(null), repository.messageCursors)
    }

    @Test
    fun `the tabs show before the scan of another tab ends`() = runTest {
        repository.addQuestion("t1")
        repository.messagesGate = CompletableDeferred()
        val viewModel = WorkspaceDetailViewModel(repository, "w1", OpenQuestionTracker(QuestionScanner(repository)), initialSessionId = "s2")
        advanceUntilIdle()

        assertEquals(listOf(false, false), (viewModel.uiState.value.detail as LoadState.Loaded).value.sessions.map { it.hasOpenQuestion })

        repository.messagesGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(true, false), (viewModel.uiState.value.detail as LoadState.Loaded).value.sessions.map { it.hasOpenQuestion })
    }

    @Test
    fun `the open tab is not scanned, because its own pane reads the transcript`() = runTest {
        repository.addQuestion("t1")
        val viewModel = WorkspaceDetailViewModel(repository, "w1", OpenQuestionTracker(QuestionScanner(repository)))
        advanceUntilIdle()

        assertEquals("s1", viewModel.uiState.value.selectedSessionId)
        assertEquals(emptyList<String?>(), repository.messageCursors)
    }

    @Test
    fun `with two working sessions, the scan finishes first, so the session with a question opens`() = runTest {
        repository.sessionsInWorkspace = listOf(Session("s1", "conductor://1", "One"), Session("s2", "conductor://2", "Two"))
        repository.sessionStatuses = mutableMapOf("s1" to "working", "s2" to "working")
        repository.sessionTranscripts["s1"] = emptyList()
        repository.addQuestion("t1")
        val viewModel = WorkspaceDetailViewModel(repository, "w1", OpenQuestionTracker(QuestionScanner(repository)))
        advanceUntilIdle()

        assertEquals("s2", viewModel.uiState.value.selectedSessionId)
    }

    @Test
    fun `a refresh keeps the question dot of a session that still works`() = runTest {
        val viewModel = WorkspaceDetailViewModel(repository, "w1", OpenQuestionTracker(QuestionScanner(repository)))
        advanceUntilIdle()
        viewModel.recordSessionStatus("s1", AgentStatus.WORKING, hasOpenQuestion = true)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(true, false), (viewModel.uiState.value.detail as LoadState.Loaded).value.sessions.map { it.hasOpenQuestion })
    }

    @Test
    fun `rename replaces the workspace name`() = runTest {
        val viewModel = WorkspaceDetailViewModel(repository, "w1", OpenQuestionTracker(QuestionScanner(repository)))
        advanceUntilIdle()

        viewModel.rename("  Better  ")
        advanceUntilIdle()

        assertEquals("Better", (viewModel.uiState.value.detail as LoadState.Loaded).value.workspace.name)
    }

    @Test
    fun `the requested session opens first`() = runTest {
        val viewModel = WorkspaceDetailViewModel(repository, "w1", OpenQuestionTracker(QuestionScanner(repository)), initialSessionId = "s2")
        advanceUntilIdle()

        assertEquals("s2", viewModel.uiState.value.selectedSessionId)
    }

    @Test
    fun `with no request, the session with an open question opens before a working session`() = runTest {
        val rows = listOf(row("a", AgentStatus.IDLE), row("b", AgentStatus.WORKING), row("c", AgentStatus.WORKING, hasOpenQuestion = true))

        assertEquals("c", chooseSession(null, rows))
        assertEquals("b", chooseSession(null, rows.take(2)))
        assertEquals("a", chooseSession("gone", rows.take(1)))
        assertEquals(null, chooseSession(null, emptyList()))
    }

    @Test
    fun `a new session gets a tab and opens`() = runTest {
        val viewModel = WorkspaceDetailViewModel(repository, "w1", OpenQuestionTracker(QuestionScanner(repository)))
        advanceUntilIdle()
        repository.sessionsInWorkspace += Session("s-created", "conductor://x", "Quick")

        viewModel.createSession(NewSessionInput(ModelSelection("claude", "haiku-4-5", null), "Quick", ""))
        advanceUntilIdle()

        assertEquals("s-created", viewModel.uiState.value.selectedSessionId)
        assertEquals(listOf("One", "Two", "Quick"), (viewModel.uiState.value.detail as LoadState.Loaded).value.sessions.map { it.title })
        val request = repository.createdSessions.single()
        assertEquals("haiku-4-5", request.model)
        assertEquals(null, request.message)
    }

    @Test
    fun `the open session updates its own tab status`() = runTest {
        val viewModel = WorkspaceDetailViewModel(repository, "w1", OpenQuestionTracker(QuestionScanner(repository)))
        advanceUntilIdle()

        viewModel.recordSessionStatus("s1", AgentStatus.IDLE, hasOpenQuestion = false)

        assertEquals(listOf(AgentStatus.IDLE, AgentStatus.ERROR), (viewModel.uiState.value.detail as LoadState.Loaded).value.sessions.map { it.status })
    }

    @Test
    fun `archive emits an event`() = runTest {
        val viewModel = WorkspaceDetailViewModel(repository, "w1", OpenQuestionTracker(QuestionScanner(repository)))
        advanceUntilIdle()

        viewModel.eventFlow.test {
            viewModel.archive()
            assertEquals(WorkspaceEvent.Archived, awaitItem())
        }
    }

    private fun row(id: String, status: AgentStatus, hasOpenQuestion: Boolean = false) =
        SessionRow(Session(id, "conductor://$id", id), status, hasOpenQuestion)
}
