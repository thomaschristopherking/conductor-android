@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.ui.home

import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Section
import build.conductor.android.client.ui.components.LoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeConductorRepository().apply {
        allWorkspacesResult = { listOf(workspace("a", "A"), workspace("b", "B")) }
        sectionsResult = { listOf(Section("s1", "Interop", listOf("a"))) }
    }

    @Test
    fun `workspaces load grouped by section`() = runTest {
        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        val groups = (viewModel.uiState.value.groups as LoadState.Loaded).value
        assertEquals(listOf("Interop", "Other workspaces"), groups.map { it.title })
    }

    @Test
    fun `a sections failure still lists every workspace and shows a message`() = runTest {
        repository.sectionsResult = { throw ApiException.Server(503, "down") }

        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        val groups = (viewModel.uiState.value.groups as LoadState.Loaded).value
        assertEquals(listOf("a", "b"), groups.single().workspaces.map { it.id })
        assertEquals("Your sections did not load: down", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `a workspaces failure shows an error state`() = runTest {
        repository.allWorkspacesResult = { throw ApiException.Offline(IOException()) }

        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.groups is LoadState.Failed)
    }

    @Test
    fun `show archived reloads with includeArchived`() = runTest {
        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.setShowingArchived(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isShowingArchived)
        assertEquals(listOf(false, true), repository.allWorkspacesRequests)
    }
}
