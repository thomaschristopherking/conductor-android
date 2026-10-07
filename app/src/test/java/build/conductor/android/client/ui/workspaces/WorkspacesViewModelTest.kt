@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.ui.workspaces

import kotlinx.coroutines.ExperimentalCoroutinesApi
import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Page
import build.conductor.android.client.ui.components.LoadState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val viewModel = WorkspacesViewModel(repository, "p1", backgroundScope)
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
        val viewModel = WorkspacesViewModel(repository, "p1", backgroundScope)
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
        val viewModel = WorkspacesViewModel(repository, "p1", backgroundScope)
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
    fun `a swiped workspace hides at once and is archived when the undo window closes`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.archive(alpha)
        runCurrent()
        assertEquals(setOf("alpha"), viewModel.uiState.value.archivingIds)
        assertEquals(alpha, viewModel.uiState.value.undoableArchive)
        advanceTimeBy(WorkspacesViewModel.UNDO_WINDOW_MILLIS - 1)
        assertEquals(emptyList<String>(), repository.archivedWorkspaceIds)

        advanceTimeBy(2)

        assertEquals(listOf("alpha"), repository.archivedWorkspaceIds)
        assertEquals(listOf("beta"), loadedIds(viewModel))
        assertEquals(emptySet<String>(), viewModel.uiState.value.archivingIds)
        assertNull(viewModel.uiState.value.undoableArchive)
    }

    @Test
    fun `undo inside the window shows the workspace again and sends no archive call`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.archive(alpha)
        advanceTimeBy(WorkspacesViewModel.UNDO_WINDOW_MILLIS / 2)
        viewModel.undoArchive()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.archivedWorkspaceIds)
        assertEquals(emptySet<String>(), viewModel.uiState.value.archivingIds)
        assertEquals(listOf("alpha", "beta"), loadedIds(viewModel))
    }

    @Test
    fun `a second swipe archives the first workspace at once and moves undo to the second`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.archive(alpha)
        runCurrent()
        viewModel.archive(beta)
        runCurrent()

        assertEquals(listOf("alpha"), repository.archivedWorkspaceIds)
        assertEquals(beta, viewModel.uiState.value.undoableArchive)
        viewModel.undoArchive()
        advanceUntilIdle()
        assertEquals(listOf("alpha"), repository.archivedWorkspaceIds)
        assertEquals(listOf("beta"), loadedIds(viewModel))
    }

    @Test
    fun `a failed archive call shows the workspace again with an error`() = runTest {
        repository.archiveFailure = ApiException.Server(503, "down")
        val viewModel = loadedViewModel()

        viewModel.archive(alpha)
        viewModel.confirmArchive()
        runCurrent()

        assertEquals(emptySet<String>(), viewModel.uiState.value.archivingIds)
        assertEquals(listOf("alpha", "beta"), loadedIds(viewModel))
        assertEquals("Could not archive \"Alpha\". down", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `with archived workspaces shown, an archived workspace stays in the list as archived`() = runTest {
        val viewModel = loadedViewModel()
        viewModel.setShowingArchived(true)
        advanceUntilIdle()

        viewModel.archive(alpha)
        advanceTimeBy(WorkspacesViewModel.UNDO_WINDOW_MILLIS + 1)

        val archived = (viewModel.uiState.value.workspaces as LoadState.Loaded).value.first { it.id == "alpha" }
        assertEquals("archived", archived.state)
    }

    private val alpha = workspace("alpha", "Alpha", "2026-10-05T00:00:00Z")
    private val beta = workspace("beta", "Beta", "2026-10-04T00:00:00Z")

    private fun TestScope.loadedViewModel(): WorkspacesViewModel {
        repository.workspacePages = { _, _ -> Page(listOf(alpha, beta)) }
        return WorkspacesViewModel(repository, "p1", backgroundScope).also { advanceUntilIdle() }
    }

    private fun loadedIds(viewModel: WorkspacesViewModel) =
        (viewModel.uiState.value.workspaces as LoadState.Loaded).value.map { it.id }

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
