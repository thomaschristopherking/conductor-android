package build.conductor.android.client.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.ModelCatalog
import build.conductor.android.client.data.ModelSelection
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.CreateSessionRequest
import build.conductor.android.client.data.api.Session
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.toFailedState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionRow(val session: Session, val status: AgentStatus)

data class WorkspaceDetail(val workspace: Workspace, val sessions: List<SessionRow>)

data class WorkspaceDetailUiState(
    val detail: LoadState<WorkspaceDetail> = LoadState.Loading,
    val isRefreshing: Boolean = false,
    val isBusy: Boolean = false,
    val favorites: List<ModelSelection> = emptyList(),
    val defaultSelection: ModelSelection = ModelCatalog.defaultSelection(emptyList()),
    val snackbarMessage: String? = null,
)

sealed interface WorkspaceEvent {
    data class OpenSession(val sessionId: String, val title: String) : WorkspaceEvent
    data object Archived : WorkspaceEvent
}

/** What the user enters in the new-session dialog. */
data class NewSessionInput(val selection: ModelSelection, val name: String, val message: String)

class WorkspaceDetailViewModel(
    private val repository: ConductorRepository,
    private val workspaceId: String,
) : ViewModel() {
    private val state = MutableStateFlow(WorkspaceDetailUiState())
    val uiState: StateFlow<WorkspaceDetailUiState> = state.asStateFlow()
    private val events = Channel<WorkspaceEvent>(Channel.BUFFERED)
    val eventFlow: Flow<WorkspaceEvent> = events.receiveAsFlow()

    init {
        load()
        viewModelScope.launch { loadFavorites() }
    }

    fun load() {
        state.update { it.copy(detail = LoadState.Loading) }
        viewModelScope.launch { fetchDetail() }
    }

    fun refresh() {
        state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { fetchDetail() }
    }

    private suspend fun fetchDetail() {
        try {
            val detail = coroutineScope {
                val workspace = async { repository.workspace(workspaceId) }
                val sessions = async { withStatuses(repository.sessions(workspaceId)) }
                WorkspaceDetail(workspace.await(), sessions.await())
            }
            state.update { it.copy(detail = LoadState.Loaded(detail), isRefreshing = false) }
        } catch (exception: ApiException) {
            state.update { it.withFailure(exception) }
        }
    }

    /** One failed status call shows that session as unknown; it does not fail the screen. */
    private suspend fun withStatuses(sessions: List<Session>): List<SessionRow> = coroutineScope {
        sessions.map { session -> async { SessionRow(session, statusOf(session.id)) } }.awaitAll()
    }

    private suspend fun statusOf(sessionId: String): AgentStatus = try {
        AgentStatus.from(repository.sessionStatus(sessionId).status)
    } catch (_: ApiException) {
        AgentStatus.UNKNOWN
    }

    private suspend fun loadFavorites() {
        val favorites = try {
            repository.favoriteModels()
        } catch (_: ApiException) {
            return
        }
        state.update { it.copy(favorites = ModelCatalog.supportedFavorites(favorites), defaultSelection = ModelCatalog.defaultSelection(favorites)) }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        runAction {
            val renamed = repository.renameWorkspace(workspaceId, trimmed)
            state.update { current -> current.copy(detail = current.detail.mapDetail { it.copy(workspace = renamed) }) }
        }
    }

    fun archive() = runAction {
        repository.archiveWorkspace(workspaceId)
        events.send(WorkspaceEvent.Archived)
    }

    fun createSession(input: NewSessionInput) = runAction {
        val request = CreateSessionRequest(
            workspaceId = workspaceId,
            agent = input.selection.agent,
            model = input.selection.model,
            effort = input.selection.effort,
            name = input.name.trim().ifEmpty { null },
            message = input.message.trim().ifEmpty { null },
        )
        val session = repository.createSession(request)
        events.send(WorkspaceEvent.OpenSession(session.id, session.name ?: "New session"))
    }

    fun onSnackbarShown() = state.update { it.copy(snackbarMessage = null) }

    private fun runAction(action: suspend () -> Unit) {
        if (state.value.isBusy) return
        state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                action()
            } catch (exception: ApiException) {
                state.update { it.copy(snackbarMessage = exception.message) }
            }
            state.update { it.copy(isBusy = false) }
        }
    }

    private fun WorkspaceDetailUiState.withFailure(exception: ApiException): WorkspaceDetailUiState =
        if (detail is LoadState.Loaded) {
            copy(isRefreshing = false, snackbarMessage = exception.message)
        } else {
            copy(isRefreshing = false, detail = exception.toFailedState())
        }
}

private fun LoadState<WorkspaceDetail>.mapDetail(transform: (WorkspaceDetail) -> WorkspaceDetail): LoadState<WorkspaceDetail> =
    if (this is LoadState.Loaded) LoadState.Loaded(transform(value)) else this
