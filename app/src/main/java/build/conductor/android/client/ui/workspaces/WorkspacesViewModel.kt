package build.conductor.android.client.ui.workspaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.ArchiveState
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.WorkspaceArchiver
import build.conductor.android.client.data.applyTo
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.data.github.StackPosition
import build.conductor.android.client.data.github.StackPositions
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.toFailedState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspacesUiState(
    val workspaces: LoadState<List<Workspace>> = LoadState.Loading,
    val isShowingArchived: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val snackbarMessage: String? = null,
    val archive: ArchiveState = ArchiveState(),
    /** The place of each workspace in its pull request stack, by workspace id. */
    val stackPositions: Map<String, StackPosition> = emptyMap(),
)

/** The loaded workspaces without the ones that the user archived. */
val WorkspacesUiState.visibleWorkspaces: LoadState<List<Workspace>>
    get() = (workspaces as? LoadState.Loaded)?.let { LoadState.Loaded(archive.applyTo(it.value, isShowingArchived)) } ?: workspaces

class WorkspacesViewModel(
    private val repository: ConductorRepository,
    private val projectId: String,
    private val archiver: WorkspaceArchiver,
    private val stackPositions: StackPositions = StackPositions.None,
) : ViewModel(), WorkspaceArchiving by SharedWorkspaceArchiving(archiver) {
    private val state = MutableStateFlow(WorkspacesUiState())
    val uiState: StateFlow<WorkspacesUiState> = state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch { archiver.archiveState.collect { archive -> state.update { it.copy(archive = archive) } } }
        viewModelScope.launch { stackPositions.positions.collect { positions -> state.update { it.copy(stackPositions = positions) } } }
    }

    fun load() {
        state.update { it.copy(workspaces = LoadState.Loading) }
        reloadFirstPage()
    }

    fun refresh() {
        state.update { it.copy(isRefreshing = true) }
        reloadFirstPage()
    }

    fun setShowingArchived(isShowingArchived: Boolean) {
        state.update { it.copy(isShowingArchived = isShowingArchived, workspaces = LoadState.Loading) }
        reloadFirstPage()
    }

    fun loadMore() {
        val loaded = (state.value.workspaces as? LoadState.Loaded)?.value ?: return
        if (!state.value.hasMore || state.value.isLoadingMore || state.value.isRefreshing) return
        state.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        loadJob = viewModelScope.launch { fetchPage(offset = loaded.size, previous = loaded) }
    }

    private fun reloadFirstPage() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetchPage(offset = 0, previous = emptyList()) }
    }

    private suspend fun fetchPage(offset: Int, previous: List<Workspace>) {
        try {
            val page = repository.workspaces(projectId, state.value.isShowingArchived, offset)
            val merged = (previous + page.data).distinctBy { it.id }.sortedByDescending { it.lastActivityAt ?: it.createdAt }
            state.update { it.copy(workspaces = LoadState.Loaded(merged), hasMore = page.hasMore, isRefreshing = false, isLoadingMore = false) }
            stackPositions.load(merged)
        } catch (exception: ApiException) {
            state.update { it.withFailure(exception, isFirstPage = offset == 0) }
        }
    }

    fun onSnackbarShown() {
        state.update { it.copy(snackbarMessage = null) }
    }

    private fun WorkspacesUiState.withFailure(exception: ApiException, isFirstPage: Boolean): WorkspacesUiState {
        val settled = copy(isRefreshing = false, isLoadingMore = false)
        return when {
            !isFirstPage -> settled.copy(loadMoreError = exception.message)
            workspaces is LoadState.Loaded -> settled.copy(snackbarMessage = exception.message)
            else -> settled.copy(workspaces = exception.toFailedState())
        }
    }
}
