package build.conductor.android.client.ui.workspaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Workspace
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
)

class WorkspacesViewModel(
    private val repository: ConductorRepository,
    private val projectId: String,
) : ViewModel() {
    private val state = MutableStateFlow(WorkspacesUiState())
    val uiState: StateFlow<WorkspacesUiState> = state.asStateFlow()
    private var loadJob: Job? = null

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
        if (!state.value.hasMore || state.value.isLoadingMore) return
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
