package build.conductor.android.client.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Message
import build.conductor.android.client.data.api.SessionStatus
import build.conductor.android.client.data.transcript.Delivery
import build.conductor.android.client.data.transcript.TranscriptItem
import build.conductor.android.client.data.transcript.TranscriptParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class SessionUiState(
    val title: String,
    val deepLink: String? = null,
    val status: AgentStatus = AgentStatus.UNKNOWN,
    val statusError: String? = null,
    val items: List<TranscriptItem> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val draft: String = "",
    val isSending: Boolean = false,
    val isCancelling: Boolean = false,
    val isPolling: Boolean = false,
    val connectionProblem: String? = null,
    val isStarred: Boolean = false,
)

sealed interface SessionEvent {
    data class ShowMessage(val text: String) : SessionEvent
}

/**
 * Shows one session and polls it while the agent works and the screen is visible.
 * Each poll asks only for messages after the last message that the app already has.
 */
class SessionViewModel(
    private val repository: ConductorRepository,
    private val sessionId: String,
    title: String,
    private val starredSessions: StarredSessions = StarredSessions.None,
    private val newMessageId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val state = MutableStateFlow(SessionUiState(title = title))
    val uiState: StateFlow<SessionUiState> = state.asStateFlow()
    private val events = Channel<SessionEvent>(Channel.BUFFERED)
    val eventFlow: Flow<SessionEvent> = events.receiveAsFlow()

    private val transcript = TranscriptBuffer()
    private val pendingPrompts = mutableListOf<TranscriptItem.UserPrompt>()
    private val pollMutex = Mutex()
    private val backoff = PollBackoff()
    private var pollJob: Job? = null
    private var isVisible = false
    private var hasLoaded = false

    /** Polls that continue after a send while the status is still idle, because a queued message reports idle until delivery. */
    private var deliveryGracePolls = 0

    init {
        load()
        viewModelScope.launch {
            starredSessions.starredIds.collect { ids -> state.update { it.copy(isStarred = sessionId in ids) } }
        }
    }

    fun load() {
        state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch { loadSession() }
    }

    private suspend fun loadSession() {
        try {
            coroutineScope {
                val session = async { repository.session(sessionId) }
                pollOnce()
                val loaded = session.await()
                state.update { it.copy(title = loaded.name ?: it.title, deepLink = loaded.deepLink, isLoading = false) }
            }
            hasLoaded = true
            startPollingIfNeeded()
        } catch (exception: ApiException) {
            state.update { it.copy(isLoading = false, loadError = exception.message) }
        }
    }

    fun onVisible() {
        isVisible = true
        if (hasLoaded) refresh()
    }

    fun onHidden() {
        isVisible = false
        stopPolling()
    }

    /** Fetches new messages and the status now, then polls again if the agent works. */
    fun refresh() {
        viewModelScope.launch {
            try {
                pollOnce()
                state.update { it.copy(connectionProblem = null) }
            } catch (exception: ApiException) {
                state.update { it.copy(connectionProblem = exception.message) }
            }
            startPollingIfNeeded()
        }
    }

    fun onDraftChange(text: String) = state.update { it.copy(draft = text) }

    fun send() {
        val text = state.value.draft.trim()
        if (text.isEmpty() || state.value.isSending) return
        val messageId = newMessageId()
        pendingPrompts += TranscriptItem.UserPrompt("pending-$messageId", text, Delivery.SENDING, messageId)
        state.update { it.copy(draft = "", isSending = true) }
        publishItems()
        viewModelScope.launch { deliver(text, messageId) }
    }

    private suspend fun deliver(text: String, messageId: String) {
        try {
            val sent = repository.sendMessage(sessionId, text, messageId)
            markPending(messageId, if (sent.state == "queued") Delivery.QUEUED else Delivery.SENT)
            deliveryGracePolls = DELIVERY_GRACE_POLLS
            backoff.onNewMessages()
            startPollingIfNeeded()
        } catch (exception: ApiException) {
            pendingPrompts.removeAll { it.clientMessageId == messageId }
            state.update { it.copy(draft = it.draft.ifEmpty { text }) }
            events.send(SessionEvent.ShowMessage(exception.message ?: "The message was not sent."))
        }
        state.update { it.copy(isSending = false) }
        publishItems()
    }

    fun cancel() {
        if (state.value.isCancelling) return
        state.update { it.copy(isCancelling = true) }
        viewModelScope.launch {
            try {
                val result = repository.cancelSession(sessionId)
                state.update { it.copy(status = AgentStatus.from(result.status)) }
                startPollingIfNeeded()
            } catch (exception: ApiException) {
                events.send(SessionEvent.ShowMessage(exception.message ?: "The agent did not stop."))
            }
            state.update { it.copy(isCancelling = false) }
        }
    }

    fun toggleStar() {
        val current = state.value
        viewModelScope.launch { starredSessions.setStarred(sessionId, current.title, !current.isStarred) }
    }

    private fun shouldPoll(): Boolean =
        isVisible && hasLoaded && (state.value.status == AgentStatus.WORKING || deliveryGracePolls > 0)

    private fun startPollingIfNeeded() {
        if (pollJob?.isActive == true || !shouldPoll()) return
        pollJob = viewModelScope.launch { pollWhileNeeded() }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        state.update { it.copy(isPolling = false) }
    }

    private suspend fun pollWhileNeeded() {
        state.update { it.copy(isPolling = true) }
        while (shouldPoll()) {
            delay(backoff.currentMillis)
            pollWithBackoff()
        }
        state.update { it.copy(isPolling = false) }
    }

    private suspend fun pollWithBackoff() {
        try {
            if (pollOnce()) backoff.onNewMessages() else backoff.onNoNewMessages()
            state.update { it.copy(connectionProblem = null) }
        } catch (exception: ApiException.Unauthorized) {
            deliveryGracePolls = 0
            state.update { it.copy(status = AgentStatus.UNKNOWN, connectionProblem = exception.message) }
        } catch (exception: ApiException) {
            backoff.onFailure()
            state.update { it.copy(connectionProblem = exception.message) }
        }
    }

    /** Returns true when new messages arrived. */
    private suspend fun pollOnce(): Boolean = pollMutex.withLock {
        coroutineScope {
            val status = async { repository.sessionStatus(sessionId) }
            val newItems = fetchNewItems()
            applyStatus(status.await(), newItems)
            newItems.isNotEmpty()
        }
    }

    private suspend fun fetchNewItems(): List<TranscriptItem> {
        val added = mutableListOf<TranscriptItem>()
        do {
            val page = repository.messagesAfter(sessionId, transcript.lastMessageId)
            added += transcript.append(page.data)
        } while (page.hasMore && page.data.isNotEmpty())
        return added
    }

    private fun applyStatus(status: SessionStatus, newItems: List<TranscriptItem>) {
        val agentStatus = AgentStatus.from(status.status)
        if (agentStatus == AgentStatus.WORKING || newItems.any { it is TranscriptItem.TurnEnd }) {
            deliveryGracePolls = 0
        } else if (deliveryGracePolls > 0) {
            deliveryGracePolls--
        }
        pendingPrompts.removeAll { pending -> transcript.containsPrompt(pending) }
        state.update { it.copy(status = agentStatus, statusError = status.errorMessage ?: status.lastError) }
        publishItems()
    }

    private fun markPending(messageId: String, delivery: Delivery) {
        val index = pendingPrompts.indexOfFirst { it.clientMessageId == messageId }
        if (index >= 0) pendingPrompts[index] = pendingPrompts[index].copy(delivery = delivery)
    }

    private fun publishItems() {
        state.update { it.copy(items = transcript.items + pendingPrompts) }
    }

    private companion object {
        const val DELIVERY_GRACE_POLLS = 10
    }
}

/** The parsed transcript, and the id of the last message, for the next `after` query. */
private class TranscriptBuffer {
    private val seenMessageIds = mutableSetOf<String>()
    var items: List<TranscriptItem> = emptyList()
        private set
    var lastMessageId: String? = null
        private set

    fun append(messages: List<Message>): List<TranscriptItem> {
        val fresh = messages.filter { seenMessageIds.add(it.id) }
        messages.lastOrNull()?.let { lastMessageId = it.id }
        val added = TranscriptParser.parse(fresh)
        items = items + added
        return added
    }

    fun containsPrompt(pending: TranscriptItem.UserPrompt): Boolean = items.any {
        it is TranscriptItem.UserPrompt && (it.clientMessageId == pending.clientMessageId || it.text == pending.text)
    }
}

/** The sessions that the user starred for notifications. */
interface StarredSessions {
    val starredIds: Flow<Set<String>>
    suspend fun setStarred(sessionId: String, title: String, isStarred: Boolean)

    object None : StarredSessions {
        override val starredIds: Flow<Set<String>> = flowOf(emptySet())
        override suspend fun setStarred(sessionId: String, title: String, isStarred: Boolean) = Unit
    }
}
