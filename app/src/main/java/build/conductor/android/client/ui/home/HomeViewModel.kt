package build.conductor.android.client.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Section
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.toFailedState
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
)

/** Lists every workspace that the user can see, grouped by the user's sections. */
class HomeViewModel(private val repository: ConductorRepository) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
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

    private suspend fun fetchGroups() = coroutineScope {
        val sections = async { fetchSections() }
        try {
            val workspaces = repository.allWorkspaces(state.value.isShowingArchived)
            showGroups(workspaces, sections.await())
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

    private fun HomeUiState.withFailure(exception: ApiException): HomeUiState {
        val settled = copy(isRefreshing = false)
        return if (groups is LoadState.Loaded) settled.copy(snackbarMessage = exception.message) else settled.copy(groups = exception.toFailedState())
    }
}
