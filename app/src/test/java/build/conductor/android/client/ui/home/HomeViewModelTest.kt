@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.ui.home

import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.HomeSnapshot
import build.conductor.android.client.data.HomeSnapshots
import build.conductor.android.client.data.WorkspaceArchiver
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Section
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.data.github.StackPosition
import build.conductor.android.client.data.github.StackPositions
import kotlinx.coroutines.flow.MutableStateFlow
import build.conductor.android.client.ui.components.LoadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val snapshots = MemoryHomeSnapshots()

    private val repository = FakeConductorRepository().apply {
        allWorkspacesResult = { listOf(workspace("a", "A"), workspace("b", "B")) }
        sectionsResult = { listOf(Section("s1", "Interop", listOf("a"))) }
    }

    @Test
    fun `workspaces load grouped by section`() = runTest {
        val viewModel = HomeViewModel(repository, WorkspaceArchiver(repository, backgroundScope))
        advanceUntilIdle()

        val groups = (viewModel.uiState.value.groups as LoadState.Loaded).value
        assertEquals(listOf("Interop", "Other workspaces"), groups.map { it.title })
    }

    @Test
    fun `the loaded workspaces go to the stack positions, and the positions show in the state`() = runTest {
        val stackPositions = RecordingStackPositions()
        val viewModel = HomeViewModel(repository, WorkspaceArchiver(repository, backgroundScope), stackPositions = stackPositions)
        advanceUntilIdle()

        stackPositions.positions.value = mapOf("a" to StackPosition(3, 12))
        runCurrent()

        assertEquals(listOf(listOf("a", "b")), stackPositions.loads.map { workspaces -> workspaces.map { it.id }.sorted() })
        assertEquals(mapOf("a" to StackPosition(3, 12)), viewModel.uiState.value.stackPositions)
    }

    @Test
    fun `a workspace archived on another screen leaves the home list, and an empty section goes away`() = runTest {
        val archiver = WorkspaceArchiver(repository, backgroundScope)
        val viewModel = HomeViewModel(repository, archiver)
        advanceUntilIdle()

        archiver.archive(workspace("a", "A"))
        runCurrent()

        val groups = (viewModel.uiState.value.visibleGroups as LoadState.Loaded).value
        assertEquals(listOf("b"), groups.flatMap { group -> group.workspaces.map { it.id } })
    }

    @Test
    fun `a sections failure still lists every workspace and shows a message`() = runTest {
        repository.sectionsResult = { throw ApiException.Server(503, "down") }

        val viewModel = HomeViewModel(repository, WorkspaceArchiver(repository, backgroundScope))
        advanceUntilIdle()

        val groups = (viewModel.uiState.value.groups as LoadState.Loaded).value
        assertEquals(listOf("a", "b"), groups.single().workspaces.map { it.id })
        assertEquals("Your sections did not load: down", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `a workspaces failure shows an error state`() = runTest {
        repository.allWorkspacesResult = { throw ApiException.Offline(IOException()) }

        val viewModel = HomeViewModel(repository, WorkspaceArchiver(repository, backgroundScope))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.groups is LoadState.Failed)
    }

    @Test
    fun `the saved list shows at once, then the list from the API replaces it`() = runTest {
        snapshots.saved = HomeSnapshot(listOf(workspace("old", "Old")), emptyList())
        repository.allWorkspacesGate = CompletableDeferred()
        val viewModel = HomeViewModel(repository, WorkspaceArchiver(repository, backgroundScope), snapshots)
        runCurrent()

        assertEquals(listOf("old"), loadedIds(viewModel))
        assertTrue(viewModel.uiState.value.isRefreshing)

        repository.allWorkspacesGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), loadedIds(viewModel))
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `an offline start keeps the saved list and shows a message`() = runTest {
        snapshots.saved = HomeSnapshot(listOf(workspace("old", "Old")), emptyList())
        repository.allWorkspacesResult = { throw ApiException.Offline(IOException()) }

        val viewModel = HomeViewModel(repository, WorkspaceArchiver(repository, backgroundScope), snapshots)
        advanceUntilIdle()

        assertEquals(listOf("old"), loadedIds(viewModel))
        assertNotNull(viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `the list from the API is saved for the next start, but the archived list is not`() = runTest {
        val viewModel = HomeViewModel(repository, WorkspaceArchiver(repository, backgroundScope), snapshots)
        advanceUntilIdle()
        val saved = snapshots.saved

        viewModel.setShowingArchived(true)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), saved?.workspaces?.map { it.id })
        assertEquals(listOf("s1"), saved?.sections?.map { it.id })
        assertSame(saved, snapshots.saved)
    }

    @Test
    fun `show archived reloads with includeArchived`() = runTest {
        val viewModel = HomeViewModel(repository, WorkspaceArchiver(repository, backgroundScope))
        advanceUntilIdle()

        viewModel.setShowingArchived(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isShowingArchived)
        assertEquals(listOf(false, true), repository.allWorkspacesRequests)
    }

    private fun loadedIds(viewModel: HomeViewModel): List<String> =
        (viewModel.uiState.value.groups as LoadState.Loaded).value.flatMap { group -> group.workspaces.map { it.id } }.distinct()
}

private class MemoryHomeSnapshots : HomeSnapshots {
    var saved: HomeSnapshot? = null

    override suspend fun read(): HomeSnapshot? = saved

    override suspend fun write(snapshot: HomeSnapshot) {
        saved = snapshot
    }

    override suspend fun clear() {
        saved = null
    }
}

private class RecordingStackPositions : StackPositions {
    override val positions = MutableStateFlow<Map<String, StackPosition>>(emptyMap())
    val loads = mutableListOf<List<Workspace>>()

    override fun load(workspaces: List<Workspace>) {
        loads += workspaces
    }
}
