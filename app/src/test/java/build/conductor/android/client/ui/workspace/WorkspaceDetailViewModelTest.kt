@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.ui.workspace

import kotlinx.coroutines.ExperimentalCoroutinesApi
import app.cash.turbine.test
import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ModelSelection
import build.conductor.android.client.data.api.Session
import build.conductor.android.client.ui.components.LoadState
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
        val viewModel = WorkspaceDetailViewModel(repository, "w1")
        advanceUntilIdle()

        val detail = (viewModel.uiState.value.detail as LoadState.Loaded).value
        assertEquals(listOf(AgentStatus.WORKING, AgentStatus.ERROR), detail.sessions.map { it.status })
        assertEquals("Workspace w1", detail.workspace.name)
    }

    @Test
    fun `rename replaces the workspace name`() = runTest {
        val viewModel = WorkspaceDetailViewModel(repository, "w1")
        advanceUntilIdle()

        viewModel.rename("  Better  ")
        advanceUntilIdle()

        assertEquals("Better", (viewModel.uiState.value.detail as LoadState.Loaded).value.workspace.name)
    }

    @Test
    fun `archive and create session emit navigation events`() = runTest {
        val viewModel = WorkspaceDetailViewModel(repository, "w1")
        advanceUntilIdle()

        viewModel.eventFlow.test {
            viewModel.createSession(NewSessionInput(ModelSelection("claude", "haiku-4-5", null), "Quick", ""))
            assertEquals(WorkspaceEvent.OpenSession("s-created", "Quick"), awaitItem())
            viewModel.archive()
            assertEquals(WorkspaceEvent.Archived, awaitItem())
        }
        val request = repository.createdSessions.single()
        assertEquals("haiku-4-5", request.model)
        assertEquals(null, request.message)
    }
}
