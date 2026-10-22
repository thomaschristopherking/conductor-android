package build.conductor.android.client.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.ArchiveState
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.HomeSnapshot
import build.conductor.android.client.data.HomeSnapshots
import build.conductor.android.client.data.WorkspaceArchiver
import build.conductor.android.client.data.applyTo
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Section
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.toFailedState
import build.conductor.android.client.ui.workspaces.SharedWorkspaceArchiving
import build.conductor.android.client.ui.workspaces.WorkspaceArchiving
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val groups: LoadState<List<WorkspaceGroup>> = LoadState.Loading,
    val isShowingArchived: Boolean = false,
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    val archive: ArchiveState = ArchiveState(),
)

/** The groups without the workspaces that the user archived. A group that loses every workspace goes away. */
val HomeUiState.visibleGroups: LoadState<List<WorkspaceGroup>>
    get() = (groups as? LoadState.Loaded)?.let { loaded ->
        LoadState.Loaded(loaded.value.map { it.copy(workspaces = archive.applyTo(it.workspaces, isShowingArchived)) }.filter { it.workspaces.isNotEmpty() })
    } ?: groups

/** Lists every workspace that the user can see, grouped by the user's sections. */
class HomeViewModel(
    private val repository: ConductorRepository,
    archiver: WorkspaceArchiver,
    private val snapshots: HomeSnapshots = HomeSnapshots.None,
) : ViewModel(), WorkspaceArchiving by SharedWorkspaceArchiving(archiver) {
    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadJob = viewModelScope.launch {
            showSnapshot()
            fetchGroups()
        }
        viewModelScope.launch { archiver.archiveState.collect { archive -> state.update { it.copy(archive = archive) } } }
    }

    fun load() {
        state.update { it.copy(groups = LoadState.Loading) }
        reload()
    }

    fun refresh() {
        state.update { it.copy(isRefreshing = true) }
        reload()
    }

    fun setShowingArchived(isShowingArchived: Boolean) {
        state.update { it.copy(isShowingArchived = isShowingArchived, groups = LoadState.Loading) }
        reload()
    }

    fun onSnackbarShown() {
        state.update { it.copy(snackbarMessage = null) }
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetchGroups() }
    }

    /** The saved list shows until the API answers; it is read first, so an offline start still shows it. */
    private suspend fun showSnapshot() {
        val snapshot = snapshots.read() ?: return
        state.update { it.copy(groups = LoadState.Loaded(groupBySection(snapshot.workspaces, snapshot.sections)), isRefreshing = true) }
    }

    private suspend fun fetchGroups() = coroutineScope {
        val sections = async { fetchSections() }
        try {
            val workspaces = repository.allWorkspaces(state.value.isShowingArchived)
            val loadedSections = sections.await()
            showGroups(workspaces, loadedSections)
            saveSnapshot(workspaces, loadedSections)
        } catch (exception: ApiException) {
            sections.cancel()
            state.update { it.withFailure(exception) }
        }
    }

    /** The sections endpoint is experimental, so its failure must not hide the workspaces. */
    private suspend fun fetchSections(): Result<List<Section>> = try {
        Result.success(repository.sections())
    } catch (exception: ApiException) {
        Result.failure(exception)
    }

    private fun showGroups(workspaces: List<Workspace>, sections: Result<List<Section>>) {
        val groups = groupBySection(workspaces, sections.getOrDefault(emptyList()))
        val sectionsMessage = sections.exceptionOrNull()?.let { "Your sections did not load: ${it.message}" }
        state.update { it.copy(groups = LoadState.Loaded(groups), isRefreshing = false, snackbarMessage = sectionsMessage ?: it.snackbarMessage) }
    }

    private suspend fun saveSnapshot(workspaces: List<Workspace>, sections: Result<List<Section>>) {
        if (state.value.isShowingArchived) return
        snapshots.write(HomeSnapshot(workspaces, sections.getOrNull() ?: return))
    }

    private fun HomeUiState.withFailure(exception: ApiException): HomeUiState {
        val settled = copy(isRefreshing = false)
        return if (groups is LoadState.Loaded) settled.copy(snackbarMessage = exception.message) else settled.copy(groups = exception.toFailedState())
    }
}
