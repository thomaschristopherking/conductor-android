package build.conductor.android.client.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Message
import build.conductor.android.client.data.settings.StarredSessions
import build.conductor.android.client.data.api.SessionStatus
import build.conductor.android.client.data.transcript.Delivery
import build.conductor.android.client.data.transcript.Question
import build.conductor.android.client.data.transcript.QuestionAnswer
import build.conductor.android.client.data.transcript.TranscriptItem
import build.conductor.android.client.data.transcript.TranscriptParser
import build.conductor.android.client.data.transcript.attachQuestionResults
import build.conductor.android.client.data.transcript.findOpenQuestion
import build.conductor.android.client.data.transcript.formatAnswers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** A question that the agent waits on; the API still reports the status as working. */
    val openQuestion: TranscriptItem.Questions? = null,
    /** One answer for each question in [openQuestion]. */
    val questionAnswers: List<QuestionAnswer> = emptyList(),
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
    private val pendingPrompts = mutableListOf<PendingPrompt>()

    /** Questions whose answers the app sent; the form closes before the agent's tool result arrives. */
    private val answeredQuestionIds = mutableSetOf<String>()

    /** The text and id of a prompt whose send failed; a resend of the same text reuses the id, so the server can drop a duplicate. */
    private var unsentPrompt: Pair<String, String>? = null
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
        state.update { it.copy(draft = "") }
        submitPrompt(text, onFailure = { state.update { it.copy(draft = it.draft.ifEmpty { text }) } })
    }

    fun toggleOption(questionIndex: Int, label: String) = updateAnswer(questionIndex) { answer, question -> answer.withToggled(label, question) }

    fun onOtherTextChange(questionIndex: Int, text: String) = updateAnswer(questionIndex) { answer, question -> answer.withOtherText(text, question) }

    /** Sends the chosen answers as one message. A message closes Conductor's question form, and the agent reads the answers from it. */
    fun sendAnswers() {
        val question = state.value.openQuestion ?: return
        val answers = state.value.questionAnswers
        if (answers.none { it.isAnswered } || state.value.isSending) return
        answeredQuestionIds += question.toolUseId
        submitPrompt(formatAnswers(question.questions, answers), onFailure = { answeredQuestionIds -= question.toolUseId })
    }

    private fun updateAnswer(questionIndex: Int, change: (QuestionAnswer, Question) -> QuestionAnswer) {
        val question = state.value.openQuestion?.questions?.getOrNull(questionIndex) ?: return
        state.update { current ->
            current.copy(questionAnswers = current.questionAnswers.mapIndexed { index, answer -> if (index == questionIndex) change(answer, question) else answer })
        }
    }

    private fun submitPrompt(text: String, onFailure: () -> Unit) {
        val messageId = unsentPrompt?.takeIf { it.first == text }?.second ?: newMessageId()
        val prompt = TranscriptItem.UserPrompt("pending-$messageId", text, Delivery.SENDING, messageId)
        pendingPrompts += PendingPrompt(prompt, transcript.items.size)
        state.update { it.copy(isSending = true) }
        publishItems()
        viewModelScope.launch { deliver(text, messageId, onFailure) }
    }

    private suspend fun deliver(text: String, messageId: String, onFailure: () -> Unit) {
        try {
            val sent = repository.sendMessage(sessionId, text, messageId)
            unsentPrompt = null
            markPending(messageId, if (sent.state == "queued") Delivery.QUEUED else Delivery.SENT)
            deliveryGracePolls = DELIVERY_GRACE_POLLS
            recordStatusForStar(AgentStatus.WORKING)
            backoff.onNewMessages()
            startPollingIfNeeded()
        } catch (exception: ApiException) {
            unsentPrompt = text to messageId
            pendingPrompts.removeAll { it.prompt.clientMessageId == messageId }
            onFailure()
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
                if (result.canceledQueuedMessages > 0) pendingPrompts.removeAll { it.prompt.delivery == Delivery.QUEUED }
                deliveryGracePolls = 0
                state.update { it.copy(status = AgentStatus.from(result.status)) }
                publishItems()
                startPollingIfNeeded()
            } catch (exception: ApiException) {
                events.send(SessionEvent.ShowMessage(exception.message ?: "The agent did not stop."))
            }
            state.update { it.copy(isCancelling = false) }
        }
    }

    fun toggleStar() {
        val current = state.value
        viewModelScope.launch {
            if (current.isStarred) starredSessions.unstar(sessionId) else starredSessions.star(sessionId, current.title, current.status.apiValue)
        }
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

    /** Returns true when new messages arrived. The status comes first, so messages written before an idle status are never missed. */
    private suspend fun pollOnce(): Boolean = pollMutex.withLock {
        val status = repository.sessionStatus(sessionId)
        val newItems = fetchNewItems()
        applyStatus(status, newItems)
        newItems.isNotEmpty()
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
        pendingPrompts.removeAll { pending -> transcript.containsPrompt(pending) }
        updateDeliveryGrace(agentStatus, newItems)
        if (agentStatus != state.value.status) recordStatusForStar(agentStatus)
        state.update { it.copy(status = agentStatus, statusError = status.errorMessage ?: status.lastError) }
        publishItems()
    }

    /** A prompt that waits for delivery keeps the grace polls; the end of an earlier turn does not mean that it was delivered. */
    private fun updateDeliveryGrace(agentStatus: AgentStatus, newItems: List<TranscriptItem>) {
        val isAwaitingDelivery = pendingPrompts.any { it.prompt.delivery != Delivery.SENDING }
        val isTurnSettled = agentStatus == AgentStatus.WORKING || newItems.any { it is TranscriptItem.TurnEnd }
        deliveryGracePolls = when {
            !isAwaitingDelivery && isTurnSettled -> 0
            agentStatus == AgentStatus.WORKING -> deliveryGracePolls
            else -> (deliveryGracePolls - 1).coerceAtLeast(0)
        }
    }

    private fun recordStatusForStar(agentStatus: AgentStatus) {
        val apiValue = agentStatus.apiValue ?: return
        if (state.value.isStarred) viewModelScope.launch { starredSessions.recordStatus(sessionId, apiValue) }
    }

    private fun markPending(messageId: String, delivery: Delivery) {
        val index = pendingPrompts.indexOfFirst { it.prompt.clientMessageId == messageId }
        if (index >= 0) pendingPrompts[index] = pendingPrompts[index].let { it.copy(prompt = it.prompt.copy(delivery = delivery)) }
    }

    private fun publishItems() {
        val items = attachQuestionResults(transcript.items)
        val openQuestion = findOpenQuestion(items)?.takeIf { state.value.status == AgentStatus.WORKING && it.toolUseId !in answeredQuestionIds }
        state.update { current ->
            current.copy(
                items = items + pendingPrompts.map { it.prompt },
                openQuestion = openQuestion,
                questionAnswers = answersFor(openQuestion, current),
            )
        }
    }

    /** Keeps the user's choices while the same question stays open. */
    private fun answersFor(question: TranscriptItem.Questions?, current: SessionUiState): List<QuestionAnswer> = when {
        question == null -> emptyList()
        current.openQuestion?.toolUseId == question.toolUseId -> current.questionAnswers
        else -> List(question.questions.size) { QuestionAnswer() }
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

    /** Matches on the client id, or on the text of a prompt that arrived after the send, so an older prompt with the same text does not match. */
    fun containsPrompt(pending: PendingPrompt): Boolean =
        items.any { it is TranscriptItem.UserPrompt && it.clientMessageId == pending.prompt.clientMessageId } ||
            items.drop(pending.transcriptSizeAtSend).any { it is TranscriptItem.UserPrompt && it.text == pending.prompt.text }
}

/** A prompt that the transcript does not show yet. [transcriptSizeAtSend] is the transcript length when the user sent it. */
private data class PendingPrompt(val prompt: TranscriptItem.UserPrompt, val transcriptSizeAtSend: Int)
