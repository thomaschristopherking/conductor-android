package build.conductor.android.client.ui.workspaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.WorkspaceState
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.toFailedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Workspaces that the user swiped away. The list hides them until the archive call fails or the user undoes it. */
    val archivingIds: Set<String> = emptySet(),
    /** The last swiped workspace, while its undo window is open. */
    val undoableArchive: Workspace? = null,
)

/** [archiveScope] outlives the screen, so an archive that the user did not undo still happens after they leave. */
class WorkspacesViewModel(
    private val repository: ConductorRepository,
    private val projectId: String,
    private val archiveScope: CoroutineScope,
) : ViewModel() {
    private val state = MutableStateFlow(WorkspacesUiState())
    val uiState: StateFlow<WorkspacesUiState> = state.asStateFlow()
    private var loadJob: Job? = null
    private var undoWindowJob: Job? = null

    init {
        load()
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
        } catch (exception: ApiException) {
            state.update { it.withFailure(exception, isFirstPage = offset == 0) }
        }
    }

    /** Hides the workspace now, and archives it when the undo window closes. */
    fun archive(workspace: Workspace) {
        if (WorkspaceState.from(workspace.state) == WorkspaceState.ARCHIVED) return
        confirmArchive()
        state.update { it.copy(archivingIds = it.archivingIds + workspace.id, undoableArchive = workspace) }
        undoWindowJob = launchOnMain {
            delay(UNDO_WINDOW_MILLIS)
            sendArchive(workspace)
        }
    }

    fun undoArchive() {
        val workspace = state.value.undoableArchive ?: return
        undoWindowJob?.cancel()
        state.update { it.copy(archivingIds = it.archivingIds - workspace.id, undoableArchive = null) }
    }

    /** Closes the undo window early and archives the workspace now. */
    fun confirmArchive() {
        val workspace = state.value.undoableArchive ?: return
        undoWindowJob?.cancel()
        launchOnMain { sendArchive(workspace) }
    }

    private fun launchOnMain(block: suspend CoroutineScope.() -> Unit): Job = archiveScope.launch(Dispatchers.Main.immediate, block = block)

    private suspend fun sendArchive(workspace: Workspace) {
        state.update { if (it.undoableArchive?.id == workspace.id) it.copy(undoableArchive = null) else it }
        try {
            repository.archiveWorkspace(workspace.id)
            state.update { it.withArchived(workspace) }
        } catch (exception: ApiException) {
            state.update { it.copy(archivingIds = it.archivingIds - workspace.id, snackbarMessage = "Could not archive \"${workspace.name}\". ${exception.message}") }
        }
    }

    private fun WorkspacesUiState.withArchived(workspace: Workspace): WorkspacesUiState {
        val loaded = (workspaces as? LoadState.Loaded)?.value ?: return copy(archivingIds = archivingIds - workspace.id)
        val remaining = if (isShowingArchived) {
            loaded.map { if (it.id == workspace.id) it.copy(state = ARCHIVED_STATE, lifecycleStep = null) else it }
        } else {
            loaded.filterNot { it.id == workspace.id }
        }
        return copy(workspaces = LoadState.Loaded(remaining), archivingIds = archivingIds - workspace.id)
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

    companion object {
        const val UNDO_WINDOW_MILLIS = 5_000L
        private val ARCHIVED_STATE = WorkspaceState.ARCHIVED.name.lowercase()
    }
}
