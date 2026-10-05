package build.conductor.android.client.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Project
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.toFailedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: LoadState<List<Project>> = LoadState.Loading,
    val isRefreshing: Boolean = false,
)

class ProjectsViewModel(private val repository: ConductorRepository) : ViewModel() {
    private val state = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = state.asStateFlow()

    init {
        load()
    }

    fun load() {
        state.update { it.copy(projects = LoadState.Loading) }
        viewModelScope.launch { fetchProjects() }
    }

    fun refresh() {
        state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { fetchProjects() }
    }

    private suspend fun fetchProjects() {
        val result = try {
            LoadState.Loaded(repository.projects().sortedBy { it.name.lowercase() })
        } catch (exception: ApiException) {
            exception.toFailedState()
        }
        state.update { it.copy(projects = result, isRefreshing = false) }
    }
}
