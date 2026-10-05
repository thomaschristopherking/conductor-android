@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.ui.workspaces

import kotlinx.coroutines.ExperimentalCoroutinesApi
import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Page
import build.conductor.android.client.ui.components.LoadState
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class WorkspacesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeConductorRepository()

    @Test
    fun `workspaces load newest activity first and load more appends the next page`() = runTest {
        repository.workspacePages = { offset, _ ->
            if (offset == 0) {
                Page(listOf(workspace("old", "Old", "2026-10-01T00:00:00Z"), workspace("new", "New", "2026-10-04T00:00:00Z")), 0, hasMore = true)
            } else {
                Page(listOf(workspace("third", "Third", "2026-10-02T00:00:00Z")), offset, hasMore = false)
            }
        }
        val viewModel = WorkspacesViewModel(repository, "p1")
        advanceUntilIdle()
        assertEquals(listOf("new", "old"), (viewModel.uiState.value.workspaces as LoadState.Loaded).value.map { it.id })

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("new", "third", "old"), (viewModel.uiState.value.workspaces as LoadState.Loaded).value.map { it.id })
        assertFalse(viewModel.uiState.value.hasMore)
        assertEquals(listOf(0 to false, 2 to false), repository.workspaceRequests)
    }

    @Test
    fun `show archived reloads with includeArchived`() = runTest {
        val viewModel = WorkspacesViewModel(repository, "p1")
        advanceUntilIdle()

        viewModel.setShowingArchived(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isShowingArchived)
        assertEquals(0 to true, repository.workspaceRequests.last())
    }

    @Test
    fun `a first load failure shows an error state, and a refresh failure keeps the list`() = runTest {
        var failure: ApiException? = ApiException.Offline(IOException())
        repository.workspacePages = { _, _ -> failure?.let { throw it } ?: Page(listOf(workspace("w1", "One"))) }
        val viewModel = WorkspacesViewModel(repository, "p1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.workspaces is LoadState.Failed)

        failure = null
        viewModel.load()
        advanceUntilIdle()
        failure = ApiException.Server(503, "down")
        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workspaces is LoadState.Loaded)
        assertEquals("down", viewModel.uiState.value.snackbarMessage)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `the create request leaves blank optional fields out`() {
        val form = CreateWorkspaceForm(name = "  ", branch = " main ", message = "")

        val request = form.toRequest("p1")

        assertEquals(null, request.name)
        assertEquals("main", request.branch)
        assertEquals(null, request.message)
        assertEquals("claude", request.agent)
    }

    @Test
    fun `repository names come from the URL`() {
        assertEquals("example-org/example-repo", repositoryName("https://github.com/example-org/example-repo.git"))
    }
}
