package build.conductor.android.client.ui.workspaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.ModelCatalog
import build.conductor.android.client.data.ModelSelection
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.CreateWorkspaceRequest
import build.conductor.android.client.data.api.CreatedWorkspace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateWorkspaceForm(
    val name: String = "",
    val branch: String = "",
    val message: String = "",
    val selection: ModelSelection = ModelCatalog.defaultSelection(emptyList()),
    val favorites: List<ModelSelection> = emptyList(),
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

class CreateWorkspaceViewModel(
    private val repository: ConductorRepository,
    private val projectId: String,
) : ViewModel() {
    private val state = MutableStateFlow(CreateWorkspaceForm())
    val uiState: StateFlow<CreateWorkspaceForm> = state.asStateFlow()
    private val created = Channel<CreatedWorkspace>(Channel.BUFFERED)
    val createdEvents: Flow<CreatedWorkspace> = created.receiveAsFlow()
    private var hasUserChosenModel = false

    init {
        viewModelScope.launch { loadFavorites() }
    }

    private suspend fun loadFavorites() {
        val favorites = try {
            repository.favoriteModels()
        } catch (_: ApiException) {
            return
        }
        state.update { form ->
            val selection = if (hasUserChosenModel) form.selection else ModelCatalog.defaultSelection(favorites)
            form.copy(favorites = ModelCatalog.supportedFavorites(favorites), selection = selection)
        }
    }

    fun onNameChange(value: String) = state.update { it.copy(name = value) }

    fun onBranchChange(value: String) = state.update { it.copy(branch = value) }

    fun onMessageChange(value: String) = state.update { it.copy(message = value) }

    fun onSelectionChange(selection: ModelSelection) {
        hasUserChosenModel = true
        state.update { it.copy(selection = selection) }
    }

    /** Creation is not idempotent, so a failed request is never retried without the user. */
    fun submit() {
        val form = state.value
        if (form.isSubmitting) return
        state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                created.send(repository.createWorkspace(form.toRequest(projectId)))
            } catch (exception: ApiException) {
                state.update { it.copy(error = exception.message) }
            }
            state.update { it.copy(isSubmitting = false) }
        }
    }
}

internal fun CreateWorkspaceForm.toRequest(projectId: String) = CreateWorkspaceRequest(
    projectId = projectId,
    name = name.trim().ifEmpty { null },
    branch = branch.trim().ifEmpty { null },
    agent = selection.agent,
    model = selection.model,
    effort = selection.effort,
    message = message.trim().ifEmpty { null },
)
