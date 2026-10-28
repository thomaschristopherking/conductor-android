package build.conductor.android.client.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.ModelCatalog
import build.conductor.android.client.data.ModelSelection
import build.conductor.android.client.data.OpenQuestionTracker
import build.conductor.android.client.data.RecentCache
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.CreateSessionRequest
import build.conductor.android.client.data.api.Session
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.data.settings.WorkspaceSubscriptions
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.toFailedState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** True when the app notifies about the agents in this workspace. */
    val isSubscribed: Boolean = false,
    /** The session that the user closed last, while the screen offers to reopen it. */
    val closedSession: SessionRow? = null,
)

/** What the user enters in the new-session dialog. */
data class NewSessionInput(val selection: ModelSelection, val name: String, val message: String)

class WorkspaceDetailViewModel(
    private val repository: ConductorRepository,
    private val workspaceId: String,
    private val openQuestionTracker: OpenQuestionTracker,
    initialSessionId: String? = null,
    private val details: RecentCache<String, WorkspaceDetail> = RecentCache(maxEntries = 0),
    private val subscriptions: WorkspaceSubscriptions = WorkspaceSubscriptions.None,
) : ViewModel() {
    private val state = MutableStateFlow(WorkspaceDetailUiState(selectedSessionId = initialSessionId))
    val uiState: StateFlow<WorkspaceDetailUiState> = state.asStateFlow()
    private val statusPermits = Semaphore(MAX_PARALLEL_STATUS_CALLS)
    private var isFirstVisit = true

    /** A refresh that started before an archive finished still lists the session, so this keeps its tab closed. */
    private val closedSessionIds = mutableSetOf<String>()

    init {
        details[workspaceId]?.let(::showSaved) ?: load()
        viewModelScope.launch { uiState.collect { state -> (state.detail as? LoadState.Loaded)?.let { details[workspaceId] = it.value } } }
        viewModelScope.launch { loadFavorites() }
        viewModelScope.launch { subscriptions.subscribedIds.collect { ids -> state.update { it.copy(isSubscribed = workspaceId in ids) } } }
    }

    /** The tabs from the last visit show while the screen reads them again. */
    private fun showSaved(detail: WorkspaceDetail) {
        show(detail)
        refresh()
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
        if (!isFirstVisit && state.value.detail is LoadState.Loaded) refresh()
        isFirstVisit = false
    }

    /** A new subscription saves the status of each session now, so the background check notifies about the next change. */
    fun toggleSubscription() {
        val detail = (state.value.detail as? LoadState.Loaded)?.value ?: return
        val isSubscribed = state.value.isSubscribed
        viewModelScope.launch {
            if (isSubscribed) subscriptions.unsubscribe(workspaceId) else subscriptions.subscribe(workspaceId, detail.workspace.name, detail.sessionStatuses())
        }
    }

    fun selectSession(sessionId: String) = state.update { it.copy(selectedSessionId = sessionId) }

    /** Removes the tab now and archives the session. When the open tab closes, the tab next to it opens. */
    fun closeSession(row: SessionRow) {
        closedSessionIds += row.session.id
        state.update { it.withoutSession(row.session.id) }
        viewModelScope.launch { archiveClosedSession(row) }
    }

    /** Opens the tab of a closed session again. The agent does not continue the turn that the close stopped. */
    fun reopenSession(row: SessionRow) = runAction {
        repository.unarchiveSession(row.session.id)
        closedSessionIds -= row.session.id
        fetchDetail()
        selectSession(row.session.id)
    }

    /** A second close can replace [row] before its message ends; that close keeps its own message. */
    fun onClosedSessionShown(row: SessionRow) = state.update { if (it.closedSession == row) it.copy(closedSession = null) else it }

    private suspend fun archiveClosedSession(row: SessionRow) {
        try {
            repository.archiveSession(row.session.id)
            state.update { it.copy(closedSession = row) }
        } catch (exception: ApiException) {
            closedSessionIds -= row.session.id
            state.update { it.copy(snackbarMessage = "Could not close \"${row.title}\". ${exception.message}") }
            refresh()
        }
    }

    /** The open session polls its own status; this keeps its tab up to date between refreshes. */
    fun recordSessionStatus(sessionId: String, status: AgentStatus, hasOpenQuestion: Boolean) = state.update { current ->
        current.copy(detail = current.detail.mapDetail { detail -> detail.withRow(sessionId) { it.copy(status = status, hasOpenQuestion = hasOpenQuestion) } })
    }

    private suspend fun fetchDetail() {
        try {
            val detail = fetchSessionsWithStatuses()
            if (canQuestionsChooseFirstTab(detail.sessions)) {
                show(detail.copy(sessions = withQuestions(detail.sessions)))
            } else {
                show(detail)
                scanOtherTabsForQuestions(detail.sessions)
            }
        } catch (exception: ApiException) {
            state.update { it.withFailure(exception) }
        }
    }

    private fun show(fetched: WorkspaceDetail) {
        val detail = withoutClosedSessions(fetched)
        state.update { it.copy(detail = LoadState.Loaded(detail), isRefreshing = false, selectedSessionId = chooseSession(it.selectedSessionId, detail.sessions)) }
    }

    /** A list without a closed session shows that the archive is done, so a later unarchive in Conductor shows the tab again. */
    private fun withoutClosedSessions(fetched: WorkspaceDetail): WorkspaceDetail {
        closedSessionIds.retainAll(fetched.sessions.map { it.session.id }.toSet())
        return fetched.copy(sessions = fetched.sessions.filterNot { it.session.id in closedSessionIds })
    }

    private suspend fun fetchSessionsWithStatuses(): WorkspaceDetail = coroutineScope {
        val workspace = async { repository.workspace(workspaceId) }
        val sessions = async { withKnownQuestions(withStatuses(repository.sessions(workspaceId))) }
        WorkspaceDetail(workspace.await(), sessions.await())
    }

    /** One failed status call shows that session as unknown; it does not fail the screen. */
    private suspend fun withStatuses(sessions: List<Session>): List<SessionRow> = coroutineScope {
        sessions.map { session -> async { statusPermits.withPermit { SessionRow(session, statusOf(session.id)) } } }.awaitAll()
    }

    /** A refresh keeps the question dot of a session that still works until a new scan says otherwise. */
    private fun withKnownQuestions(rows: List<SessionRow>): List<SessionRow> {
        val knownIds = currentRows().filter { it.hasOpenQuestion }.map { it.session.id }.toSet()
        return rows.map { it.copy(hasOpenQuestion = it.status == AgentStatus.WORKING && it.session.id in knownIds) }
    }

    /** A question decides the first tab only when no tab is open yet and more than one agent works. */
    private fun canQuestionsChooseFirstTab(rows: List<SessionRow>): Boolean =
        rows.none { it.session.id == state.value.selectedSessionId } && rows.count { it.status == AgentStatus.WORKING } > 1

    private suspend fun withQuestions(rows: List<SessionRow>): List<SessionRow> = coroutineScope {
        rows.map { row -> async { if (row.status == AgentStatus.WORKING) row.copy(hasOpenQuestion = scanForQuestion(row)) else row } }.awaitAll()
    }

    /** The open tab reads its own transcript and reports its question, so only the other tabs need a scan. */
    private fun scanOtherTabsForQuestions(rows: List<SessionRow>) {
        val selectedId = state.value.selectedSessionId
        rows.filter { it.status == AgentStatus.WORKING && it.session.id != selectedId }.forEach { row ->
            viewModelScope.launch { recordScannedQuestion(row.session.id, scanForQuestion(row)) }
        }
    }

    private fun recordScannedQuestion(sessionId: String, hasOpenQuestion: Boolean) = state.update { current ->
        current.copy(detail = current.detail.mapDetail { detail -> detail.withRow(sessionId) { it.copy(hasOpenQuestion = it.status == AgentStatus.WORKING && hasOpenQuestion) } })
    }

    private suspend fun scanForQuestion(row: SessionRow): Boolean = statusPermits.withPermit { hasOpenQuestion(row.session.id) }

    private fun currentRows(): List<SessionRow> = (state.value.detail as? LoadState.Loaded)?.value?.sessions.orEmpty()

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

/** The tab to the right of the closed tab opens, or the tab to its left when the closed tab was the last tab. */
internal fun tabAfterClose(sessions: List<SessionRow>, closedSessionId: String): String? {
    val index = sessions.indexOfFirst { it.session.id == closedSessionId }
    return (sessions.getOrNull(index + 1) ?: sessions.getOrNull(index - 1))?.session?.id
}

private fun WorkspaceDetailUiState.withoutSession(sessionId: String): WorkspaceDetailUiState {
    val sessions = (detail as? LoadState.Loaded)?.value?.sessions.orEmpty()
    val selected = if (selectedSessionId == sessionId) tabAfterClose(sessions, sessionId) else selectedSessionId
    return copy(detail = detail.mapDetail { it.copy(sessions = it.sessions.filterNot { row -> row.session.id == sessionId }) }, selectedSessionId = selected)
}

private fun WorkspaceDetail.sessionStatuses(): Map<String, String> =
    sessions.mapNotNull { row -> row.status.apiValue?.let { row.session.id to it } }.toMap()

private fun WorkspaceDetail.withRow(sessionId: String, transform: (SessionRow) -> SessionRow): WorkspaceDetail =
    copy(sessions = sessions.map { if (it.session.id == sessionId) transform(it) else it })

private fun LoadState<WorkspaceDetail>.mapDetail(transform: (WorkspaceDetail) -> WorkspaceDetail): LoadState<WorkspaceDetail> =
    if (this is LoadState.Loaded) LoadState.Loaded(transform(value)) else this
