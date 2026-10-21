package build.conductor.android.client.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.ModelCatalog
import build.conductor.android.client.data.ModelSelection
import build.conductor.android.client.data.OpenQuestionTracker
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** [hasOpenQuestion] is true when the agent works and waits on a question. */
data class SessionRow(val session: Session, val status: AgentStatus, val hasOpenQuestion: Boolean = false)

data class WorkspaceDetail(val workspace: Workspace, val sessions: List<SessionRow>)

val SessionRow.title: String
    get() = session.name ?: "Untitled session"

data class WorkspaceDetailUiState(
    val detail: LoadState<WorkspaceDetail> = LoadState.Loading,
    val isRefreshing: Boolean = false,
    val isBusy: Boolean = false,
    val favorites: List<ModelSelection> = emptyList(),
    val defaultSelection: ModelSelection = ModelCatalog.defaultSelection(emptyList()),
    val snackbarMessage: String? = null,
    /** The session whose tab is open. */
    val selectedSessionId: String? = null,
)

sealed interface WorkspaceEvent {
    data object Archived : WorkspaceEvent
}

/** What the user enters in the new-session dialog. */
data class NewSessionInput(val selection: ModelSelection, val name: String, val message: String)

class WorkspaceDetailViewModel(
    private val repository: ConductorRepository,
    private val workspaceId: String,
    private val openQuestionTracker: OpenQuestionTracker,
    initialSessionId: String? = null,
) : ViewModel() {
    private val state = MutableStateFlow(WorkspaceDetailUiState(selectedSessionId = initialSessionId))
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

    /** Refreshes the session tabs when the screen comes back; the first load is in [init]. */
    fun onVisible() {
        if (state.value.detail is LoadState.Loaded) refresh()
    }

    fun selectSession(sessionId: String) = state.update { it.copy(selectedSessionId = sessionId) }

    /** The open session polls its own status; this keeps its tab up to date between refreshes. */
    fun recordSessionStatus(sessionId: String, status: AgentStatus, hasOpenQuestion: Boolean) = state.update { current ->
        current.copy(detail = current.detail.mapDetail { detail -> detail.withRow(sessionId) { it.copy(status = status, hasOpenQuestion = hasOpenQuestion) } })
    }

    private suspend fun fetchDetail() {
        try {
            val detail = coroutineScope {
                val workspace = async { repository.workspace(workspaceId) }
                val sessions = async { withStatuses(repository.sessions(workspaceId)) }
                WorkspaceDetail(workspace.await(), sessions.await())
            }
            state.update { it.copy(detail = LoadState.Loaded(detail), isRefreshing = false, selectedSessionId = chooseSession(it.selectedSessionId, detail.sessions)) }
        } catch (exception: ApiException) {
            state.update { it.withFailure(exception) }
        }
    }

    /** One failed status call shows that session as unknown; it does not fail the screen. */
    private suspend fun withStatuses(sessions: List<Session>): List<SessionRow> = coroutineScope {
        val permits = Semaphore(MAX_PARALLEL_STATUS_CALLS)
        sessions.map { session -> async { permits.withPermit { rowFor(session) } } }.awaitAll()
    }

    private suspend fun rowFor(session: Session): SessionRow {
        val status = statusOf(session.id)
        return SessionRow(session, status, hasOpenQuestion = status == AgentStatus.WORKING && hasOpenQuestion(session.id))
    }

    /** A failed scan shows the session as working; it does not fail the screen. */
    private suspend fun hasOpenQuestion(sessionId: String): Boolean = try {
        openQuestionTracker.openQuestionId(sessionId) != null
    } catch (_: ApiException) {
        false
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
        val row = SessionRow(session, if (request.message == null) AgentStatus.IDLE else AgentStatus.WORKING)
        state.update { current ->
            current.copy(detail = current.detail.mapDetail { it.copy(sessions = it.sessions + row) }, selectedSessionId = session.id)
        }
        fetchDetail()
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

/** A workspace can hold close to 100 sessions; this keeps the status calls from flooding the API. */
private const val MAX_PARALLEL_STATUS_CALLS = 6

/** Keeps the open tab if it still exists; otherwise opens the session that most needs the user. */
internal fun chooseSession(selectedSessionId: String?, sessions: List<SessionRow>): String? =
    sessions.firstOrNull { it.session.id == selectedSessionId }?.session?.id
        ?: sessions.firstOrNull { it.hasOpenQuestion }?.session?.id
        ?: sessions.firstOrNull { it.status == AgentStatus.WORKING }?.session?.id
        ?: sessions.firstOrNull()?.session?.id

private fun WorkspaceDetail.withRow(sessionId: String, transform: (SessionRow) -> SessionRow): WorkspaceDetail =
    copy(sessions = sessions.map { if (it.session.id == sessionId) transform(it) else it })

private fun LoadState<WorkspaceDetail>.mapDetail(transform: (WorkspaceDetail) -> WorkspaceDetail): LoadState<WorkspaceDetail> =
    if (this is LoadState.Loaded) LoadState.Loaded(transform(value)) else this
