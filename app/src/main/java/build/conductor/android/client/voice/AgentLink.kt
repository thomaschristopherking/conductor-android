package build.conductor.android.client.voice

import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.transcript.TranscriptItem
import build.conductor.android.client.data.transcript.TranscriptParser
import build.conductor.android.client.data.transcript.TranscriptSnapshot
import build.conductor.android.client.data.transcript.attachQuestionResults
import build.conductor.android.client.data.transcript.findOpenQuestion
import build.conductor.android.client.data.transcript.findOpenQuestionId
import java.util.UUID

/** What one poll found. [newItems] are the chat rows after the last poll. */
data class AgentUpdate(val previousStatus: AgentStatus, val status: AgentStatus, val statusError: String?, val newItems: List<TranscriptItem>)

/** The session when voice mode starts. [lastReply] is what the agent wrote after the last prompt, as sentences. */
data class AgentHistory(val status: AgentStatus, val openQuestion: TranscriptItem.Questions?, val lastReply: List<String>)

/** Voice mode's connection to one session: it reads only new messages, and sends and cancels for the user. */
class AgentLink(
    private val repository: ConductorRepository,
    private val sessionId: String,
    saved: TranscriptSnapshot? = null,
    private val newMessageId: () -> String = { UUID.randomUUID().toString() },
) {
    private val savedItems = saved?.items.orEmpty()
    private val seenMessageIds = saved?.messageIds.orEmpty().toMutableSet()
    private var lastMessageId = saved?.lastMessageId
    private var gracePolls = 0

    var status = AgentStatus.UNKNOWN
        private set

    /** The question that the agent waits on; the API reports the status as working meanwhile. */
    var openQuestion: TranscriptItem.Questions? = null
        private set

    /** The last tool call of the agent, for a spoken status. */
    var lastStep: TranscriptItem.ToolCall? = null
        private set

    /** True while the app must poll often: the agent works, or a sent prompt can still start a turn. */
    val isBusy: Boolean
        get() = status == AgentStatus.WORKING || gracePolls > 0

    /** Reads the status and the messages that the app does not have yet; the caller does not read them aloud. */
    suspend fun start(): AgentHistory {
        val history = savedItems + poll().newItems
        openQuestion = findOpenQuestion(attachQuestionResults(history))?.takeIf { status == AgentStatus.WORKING }
        return AgentHistory(status, openQuestion, lastReplyIn(history))
    }

    /** The status comes first, so a message written before an idle status is never missed. */
    suspend fun poll(): AgentUpdate {
        val previousStatus = status
        val sessionStatus = repository.sessionStatus(sessionId)
        val newItems = fetchNewItems()
        status = AgentStatus.from(sessionStatus.status)
        trackItems(newItems)
        updateGracePolls(previousStatus)
        return AgentUpdate(previousStatus, status, sessionStatus.errorMessage ?: sessionStatus.lastError, newItems)
    }

    /** A message closes Conductor's question form, so the open question ends here. */
    suspend fun send(text: String) {
        repository.sendMessage(sessionId, text, newMessageId())
        openQuestion = null
        gracePolls = DELIVERY_GRACE_POLLS
    }

    suspend fun cancel() {
        status = AgentStatus.from(repository.cancelSession(sessionId).status)
        gracePolls = 0
    }

    private suspend fun fetchNewItems(): List<TranscriptItem> {
        val messages = repository.newMessages(sessionId, lastMessageId)
        messages.lastOrNull()?.let { lastMessageId = it.id }
        return TranscriptParser.parse(messages.filter { seenMessageIds.add(it.id) })
    }

    private fun trackItems(newItems: List<TranscriptItem>) {
        newItems.filterIsInstance<TranscriptItem.ToolCall>().lastOrNull()?.let { lastStep = it }
        val openId = findOpenQuestionId(openQuestion?.toolUseId, newItems)
        val question = newItems.filterIsInstance<TranscriptItem.Questions>().lastOrNull { it.toolUseId == openId } ?: openQuestion?.takeIf { it.toolUseId == openId }
        openQuestion = question?.takeIf { status == AgentStatus.WORKING }
    }

    /** A queued prompt reports idle until delivery, and a new turn can follow soon after a turn ends. */
    private fun updateGracePolls(previousStatus: AgentStatus) {
        gracePolls = when {
            previousStatus == AgentStatus.WORKING && status != AgentStatus.WORKING -> maxOf(gracePolls, AFTER_TURN_GRACE_POLLS)
            status == AgentStatus.WORKING -> gracePolls
            else -> (gracePolls - 1).coerceAtLeast(0)
        }
    }

    private fun lastReplyIn(items: List<TranscriptItem>): List<String> =
        items.takeLastWhile { it !is TranscriptItem.UserPrompt }.filterIsInstance<TranscriptItem.AssistantText>().flatMap { SpeechText.sentences(it.markdown) }

    private companion object {
        const val DELIVERY_GRACE_POLLS = 10
        const val AFTER_TURN_GRACE_POLLS = 3
    }
}
