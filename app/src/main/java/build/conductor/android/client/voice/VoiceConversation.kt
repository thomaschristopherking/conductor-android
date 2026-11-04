package build.conductor.android.client.voice

import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.transcript.TranscriptItem
import build.conductor.android.client.ui.session.PollBackoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/** The session that a voice conversation talks to. */
data class VoiceTarget(val workspaceId: String, val sessionId: String, val title: String)

enum class VoicePhase {
    STARTING,

    /** The microphone is open and the user has not started to speak. */
    LISTENING,

    /** The user speaks. */
    HEARING,

    /** The app waits a moment for more words before it sends the prompt. */
    CONFIRMING,
    SENDING,
    SPEAKING,

    /** The microphone is closed; the app still reads the agent's messages aloud. */
    PAUSED,
    ENDED,
}

data class VoiceState(
    val target: VoiceTarget,
    val phase: VoicePhase = VoicePhase.STARTING,
    val agentStatus: AgentStatus = AgentStatus.UNKNOWN,
    val hasOpenQuestion: Boolean = false,
    /** The sentence that the app speaks, or the words that the user says. */
    val caption: String? = null,
    val problem: String? = null,
)

/**
 * A spoken conversation with one session. The microphone and the speaker never work at the same time:
 * the app speaks what the agent writes, then listens, and the user can interrupt it by voice or with a tap.
 * Every method runs on one thread, so the queue and counters need no lock.
 */
class VoiceConversation(target: VoiceTarget, private val io: VoiceIo, private val link: AgentLink) {
    private val state = MutableStateFlow(VoiceState(target))
    val uiState: StateFlow<VoiceState> = state.asStateFlow()

    private val speechQueue = ArrayDeque<Speech>()
    private val queuedCount = MutableStateFlow(0)
    private val taps = Channel<Unit>(Channel.CONFLATED)
    private val pollRequests = Channel<Unit>(Channel.CONFLATED)
    private var runScope: CoroutineScope? = null
    private val turn = TurnCounters()
    private val reply = ReplyMemory()
    private var isPaused = false
    private var isEnding = false
    private var needsTurnCue = false
    private var hasConnectionProblem = false
    private var hasToldAboutDistantVoice = false

    /** One button for everything: a tap interrupts speech, pauses the microphone, wakes it, or sends a prompt at once. */
    fun tap() {
        taps.trySend(Unit)
    }

    fun stopAgent() {
        runScope?.launch { stopAgentAndReport() }
    }

    /** Returns when the user ends voice mode, or when voice mode cannot work. */
    suspend fun run(): Unit = coroutineScope {
        runScope = this
        if (!io.speaker.isReady()) return@coroutineScope finish(NO_SPEECH_ENGINE)
        val history = startLink() ?: return@coroutineScope
        greet(history)
        val polling = launch { pollRepeatedly() }
        converse()
        polling.cancel()
        finish(problem = null)
    }

    private suspend fun startLink(): AgentHistory? = try {
        link.start()
    } catch (exception: ApiException) {
        finish(exception.message ?: CONNECTION_FAILED)
        null
    }

    private fun finish(problem: String?) = state.update { it.copy(phase = VoicePhase.ENDED, problem = problem ?: it.problem, caption = null) }

    private fun greet(history: AgentHistory) {
        reply.restore(history.lastReply)
        val hint = if (history.status == AgentStatus.IDLE && history.lastReply.isNotEmpty()) listOf(REPEAT_HINT) else emptyList()
        enqueue(listOf("Voice on. ${state.value.target.title}.", statusSentence()) + hint)
        history.openQuestion?.let { enqueue(SpokenQuestions.describe(it.questions)) }
        publishAgent()
    }

    private suspend fun converse() {
        while (!isEnding || speechQueue.isNotEmpty()) {
            when {
                speechQueue.isNotEmpty() -> speakNext()
                isPaused -> awaitWake()
                else -> listenForTurn()
            }
        }
    }

    // Speaking

    private suspend fun speakNext() {
        val speech = speechQueue.first()
        publishPhase(VoicePhase.SPEAKING, caption = (speech as? Speech.Say)?.text ?: state.value.caption)
        when (val interruption = speakUnlessInterrupted(speech)) {
            null -> onSpoken()
            else -> onInterrupted(interruption)
        }
    }

    private fun onSpoken() {
        dequeue()
        if (speechQueue.isNotEmpty()) return
        needsTurnCue = !isPaused
        turn.silentListens = 0
    }

    /** Returns null when the speech ended, or the cause of the interruption. */
    private suspend fun speakUnlessInterrupted(speech: Speech): Interruption? = coroutineScope {
        drainTaps()
        val finished = async<Interruption?> { perform(speech); null }
        val tapped = async { taps.receive(); Interruption.TAP }
        val voiced = async { awaitBargeIn(); Interruption.VOICE }
        select<Interruption?> { finished.onAwait { it }; tapped.onAwait { it }; voiced.onAwait { it } }.also { coroutineContext.cancelChildren() }
    }

    private suspend fun perform(speech: Speech) = when (speech) {
        is Speech.Say -> io.speaker.say(speech.text)
        is Speech.Play -> io.speaker.play(speech.cue)
    }

    private suspend fun awaitBargeIn() {
        if (turn.isBargeInEnabled) io.onsetDetector.awaitVoice() else awaitCancellation()
    }

    /** The interrupted sentence stays first in the queue, so speech resumes there when the user says nothing. */
    private suspend fun onInterrupted(interruption: Interruption) {
        when (val heard = hear()) {
            is Heard.Words -> onInterruptedByWords(interruption, heard.text)
            Heard.Silence, Heard.Distant -> if (interruption == Interruption.VOICE) onFalseBargeIn()
            is Heard.Failure -> onListenFailure(heard)
        }
    }

    private suspend fun onInterruptedByWords(interruption: Interruption, text: String) {
        if (interruption == Interruption.VOICE) turn.falseBargeIns = 0
        reply.skip(speechQueue.toList())
        speechQueue.clear()
        queuedCount.value = 0
        onWords(text)
    }

    private fun onFalseBargeIn() {
        turn.falseBargeIns++
        if (!turn.isBargeInEnabled) enqueueFirst(BARGE_IN_OFF)
    }

    // Listening

    private suspend fun listenForTurn() {
        if (needsTurnCue) io.speaker.play(Cue.YOUR_TURN)
        needsTurnCue = false
        publishPhase(VoicePhase.LISTENING, caption = null)
        when (val outcome = listenUnlessPreempted()) {
            is ListenOutcome.Done -> onHeard(outcome.heard)
            ListenOutcome.SpeechQueued -> Unit
            ListenOutcome.Tapped -> pause(announce = PAUSED)
        }
    }

    /** Agent speech that arrives before the user starts to speak closes the microphone; it waits while the user speaks. */
    private suspend fun listenUnlessPreempted(): ListenOutcome = coroutineScope {
        drainTaps()
        val progress = TurnProgress()
        val listening = async { ListenOutcome.Done(io.listener.listen(progress)) }
        val queued = async { queuedCount.first { it > 0 }; ListenOutcome.SpeechQueued }
        val tapped = async { taps.receive(); ListenOutcome.Tapped }
        val first = select<ListenOutcome> { listening.onAwait { it }; queued.onAwait { it }; tapped.onAwait { it } }
        val outcome = if (first == ListenOutcome.SpeechQueued && progress.hasSpeechStarted) listening.await() else first
        outcome.also { coroutineContext.cancelChildren() }
    }

    private suspend fun hear(): Heard {
        publishPhase(VoicePhase.LISTENING, caption = null)
        return io.listener.listen(TurnProgress())
    }

    private suspend fun onHeard(heard: Heard) = when (heard) {
        is Heard.Words -> onWords(heard.text)
        Heard.Silence -> onSilence()
        Heard.Distant -> onDistantVoice()
        is Heard.Failure -> onListenFailure(heard)
    }

    private fun onSilence() {
        turn.failures = 0
        turn.silentListens++
        if (turn.silentListens >= MAX_SILENT_LISTENS) pause(announce = PAUSED_AFTER_SILENCE)
    }

    /** The app says this once, so a TV in the room does not make it talk all the time. */
    private fun onDistantVoice() {
        if (!hasToldAboutDistantVoice) enqueue(listOf(DISTANT_VOICE))
        hasToldAboutDistantVoice = true
        onSilence()
    }

    private suspend fun onListenFailure(failure: Heard.Failure) {
        state.update { it.copy(problem = failure.message) }
        turn.failures++
        when {
            failure.isFatal -> endWith("${failure.message} Voice off.")
            turn.failures >= MAX_LISTEN_FAILURES -> pause(announce = "${failure.message} Voice paused.")
            else -> delay(LISTEN_RETRY_MILLIS)
        }
    }

    private suspend fun awaitWake() {
        publishPhase(VoicePhase.PAUSED, caption = null)
        coroutineScope {
            val tapped = async { taps.receive(); true }
            val queued = async { queuedCount.first { it > 0 }; false }
            val isTapped = select<Boolean> { tapped.onAwait { it }; queued.onAwait { it } }
            coroutineContext.cancelChildren()
            if (isTapped) wake()
        }
    }

    private fun pause(announce: String) {
        isPaused = true
        enqueue(listOf(announce))
    }

    private fun wake() {
        isPaused = false
        turn.silentListens = 0
        needsTurnCue = true
    }

    // What the user said

    private suspend fun onWords(text: String) {
        turn.silentListens = 0
        turn.failures = 0
        state.update { it.copy(caption = text, problem = null) }
        when (val utterance = VoiceCommands.interpret(text)) {
            is Utterance.Command -> runCommand(utterance.command, text)
            is Utterance.Words -> confirmAndSend(utterance.text)
        }
    }

    private suspend fun runCommand(command: VoiceCommand, text: String) {
        when (command) {
            VoiceCommand.STOP_AGENT -> stopAgentAndReport()
            VoiceCommand.REPEAT -> enqueue(reply.lastReply.ifEmpty { listOf(NOTHING_TO_REPEAT) })
            VoiceCommand.CONTINUE -> if (reply.hasSkipped) enqueue(reply.takeSkipped()) else confirmAndSend(text)
            VoiceCommand.STATUS -> enqueue(listOf(statusSentence()) + lastStepSentence())
            VoiceCommand.PAUSE -> pause(announce = PAUSED)
            VoiceCommand.END_VOICE -> endWith(VOICE_OFF)
            VoiceCommand.CANCEL -> enqueue(listOf(NOTHING_TO_CANCEL))
        }
    }

    private suspend fun stopAgentAndReport() {
        if (link.status != AgentStatus.WORKING) return enqueue(listOf(AGENT_NOT_WORKING))
        try {
            link.cancel()
            enqueue(listOf(AGENT_STOPPED))
        } catch (exception: ApiException) {
            enqueue(listOf("The agent did not stop. ${exception.message}"))
        }
        publishAgent()
    }

    private fun endWith(goodbye: String) {
        isEnding = true
        enqueue(listOf(goodbye))
    }

    /** A pause in speech often splits one prompt, so the app waits a moment for more words; "cancel" drops the prompt. */
    private suspend fun confirmAndSend(firstWords: String) {
        var prompt = firstWords
        while (true) {
            publishPhase(VoicePhase.CONFIRMING, caption = prompt)
            val more = listenForMoreWords() ?: break
            if (VoiceCommands.interpret(more) == Utterance.Command(VoiceCommand.CANCEL)) return enqueue(listOf(PROMPT_CANCELLED))
            prompt = "$prompt $more"
        }
        send(prompt)
    }

    /** Returns null when the user stays silent for the window, taps to send at once, or the recognizer fails. */
    private suspend fun listenForMoreWords(): String? = coroutineScope {
        drainTaps()
        val progress = TurnProgress()
        val listening = async { io.listener.listen(progress) }
        val tapped = async { taps.receive(); Heard.Silence }
        val early = withTimeoutOrNull(CONTINUATION_WINDOW_MILLIS) { select<Heard> { listening.onAwait { it }; tapped.onAwait { it } } }
        val heard = early ?: if (progress.hasSpeechStarted) listening.await() else Heard.Silence
        coroutineContext.cancelChildren()
        (heard as? Heard.Words)?.text
    }

    private suspend fun send(prompt: String) {
        publishPhase(VoicePhase.SENDING, caption = prompt)
        val question = link.openQuestion
        try {
            link.send(question?.let { SpokenQuestions.answer(it.questions, prompt) } ?: prompt)
            reply.startNew()
            pollRequests.trySend(Unit)
            io.speaker.play(Cue.SENT)
        } catch (exception: ApiException) {
            enqueue(listOf("The message did not send. ${exception.message}"))
        }
        publishAgent()
    }

    // The agent

    private suspend fun pollRepeatedly() {
        val backoff = PollBackoff(BUSY_POLL_MILLIS, BUSY_POLL_MAX_MILLIS, FAILURE_POLL_MAX_MILLIS)
        while (true) {
            val waitMillis = if (link.isBusy || hasConnectionProblem) backoff.currentMillis else IDLE_POLL_MILLIS
            withTimeoutOrNull(waitMillis) { pollRequests.receive() }
            pollOnce(backoff)
        }
    }

    private suspend fun pollOnce(backoff: PollBackoff) {
        try {
            val update = link.poll()
            if (update.newItems.isEmpty()) backoff.onNoNewMessages() else backoff.onNewMessages()
            onConnectionRestored()
            onAgentUpdate(update)
        } catch (exception: ApiException.Unauthorized) {
            endWith("Conductor rejected the API key. Voice off.")
        } catch (exception: ApiException) {
            backoff.onFailure()
            onConnectionLost(exception.message ?: CONNECTION_FAILED)
        }
    }

    private fun onConnectionLost(message: String) {
        state.update { it.copy(problem = message) }
        if (!hasConnectionProblem) enqueue(listOf(CONNECTION_LOST))
        hasConnectionProblem = true
    }

    private fun onConnectionRestored() {
        if (hasConnectionProblem) enqueue(listOf(CONNECTION_BACK))
        hasConnectionProblem = false
        state.update { it.copy(problem = null) }
    }

    private fun onAgentUpdate(update: AgentUpdate) {
        update.newItems.forEach(::enqueueItem)
        announceStatusChange(update)
        publishAgent()
    }

    private fun enqueueItem(item: TranscriptItem) {
        when (item) {
            is TranscriptItem.AssistantText -> enqueueReply(SpeechText.sentences(item.markdown))
            is TranscriptItem.Questions -> if (item.result == null) enqueueQuestion(item)
            is TranscriptItem.TurnEnd -> if (item.isError) enqueueReply(listOf(TURN_FAILED))
            else -> Unit
        }
    }

    /** A question needs an answer, so it opens the microphone after the app reads it. */
    private fun enqueueQuestion(item: TranscriptItem.Questions) {
        enqueueReply(SpokenQuestions.describe(item.questions))
        if (isPaused) wake()
    }

    private fun announceStatusChange(update: AgentUpdate) {
        val hasTurnEnded = update.previousStatus == AgentStatus.WORKING && update.status == AgentStatus.IDLE
        val hasFailed = update.previousStatus != AgentStatus.ERROR && update.status == AgentStatus.ERROR
        if (hasTurnEnded) enqueueCue(Cue.AGENT_DONE)
        if (hasFailed) enqueueReply(listOfNotNull(AGENT_FAILED, update.statusError))
        if (hasTurnEnded || hasFailed) onTurnOver()
    }

    /** The user's turn starts, so the microphone opens again after a pause. */
    private fun onTurnOver() {
        reply.startNew()
        if (isPaused) wake()
    }

    private fun enqueueReply(sentences: List<String>) {
        reply.add(sentences)
        enqueue(sentences)
    }

    private fun statusSentence(): String = when {
        link.openQuestion != null -> "The agent waits for your answer."
        link.status == AgentStatus.WORKING -> "The agent is working."
        link.status == AgentStatus.IDLE -> "The agent is waiting for you."
        link.status == AgentStatus.ERROR -> AGENT_FAILED
        else -> "The status of the agent is not known."
    }

    private fun lastStepSentence(): List<String> {
        val step = link.lastStep?.takeIf { link.status == AgentStatus.WORKING } ?: return emptyList()
        val summary = SpeechText.sentences(step.summary).firstOrNull()?.take(MAX_STEP_LENGTH)
        return listOf(listOfNotNull("Its last step: ${step.toolName}", summary).joinToString(", ") + ".")
    }

    // The queue and the state

    private fun enqueue(sentences: List<String>) {
        speechQueue.addAll(sentences.map(Speech::Say))
        queuedCount.value = speechQueue.size
    }

    private fun enqueueCue(cue: Cue) {
        speechQueue.add(Speech.Play(cue))
        queuedCount.value = speechQueue.size
    }

    /** Speaks [text] before the interrupted reply resumes. */
    private fun enqueueFirst(text: String) {
        speechQueue.addFirst(Speech.Say(text))
        queuedCount.value = speechQueue.size
    }

    private fun dequeue() {
        speechQueue.removeFirstOrNull()
        queuedCount.value = speechQueue.size
    }

    private fun drainTaps() {
        while (taps.tryReceive().isSuccess) Unit
    }

    private fun publishPhase(phase: VoicePhase, caption: String?) = state.update { it.copy(phase = phase, caption = caption) }

    private fun publishAgent() = state.update { it.copy(agentStatus = link.status, hasOpenQuestion = link.openQuestion != null) }

    /** Shows the user's words while they speak. */
    private inner class TurnProgress : HearingProgress {
        var hasSpeechStarted = false
            private set

        override fun onSpeechStart() {
            hasSpeechStarted = true
            publishPhase(VoicePhase.HEARING, caption = null)
        }

        override fun onPartialWords(text: String) = state.update { it.copy(caption = text) }
    }

    private companion object {
        const val MAX_SILENT_LISTENS = 6
        const val MAX_LISTEN_FAILURES = 3
        const val LISTEN_RETRY_MILLIS = 1_000L
        const val CONTINUATION_WINDOW_MILLIS = 2_500L
        const val BUSY_POLL_MILLIS = 2_000L
        const val BUSY_POLL_MAX_MILLIS = 8_000L
        const val FAILURE_POLL_MAX_MILLIS = 30_000L
        const val IDLE_POLL_MILLIS = 10_000L
        const val MAX_STEP_LENGTH = 120
        const val NO_SPEECH_ENGINE = "This phone has no text-to-speech engine. Install one in the Android settings."
        const val CONNECTION_FAILED = "Conductor did not answer."
        const val REPEAT_HINT = "Say repeat to hear its last message."
        const val PAUSED = "Paused. Tap the screen or press the headset button to talk."
        const val PAUSED_AFTER_SILENCE = "Voice paused. I still read the agent's messages. Tap the screen or press the headset button to talk."
        const val BARGE_IN_OFF = "I cannot tell your voice from mine here. To interrupt me, tap the screen or press the headset button."
        const val NOTHING_TO_REPEAT = "There is nothing to repeat."
        const val NOTHING_TO_CANCEL = "There is nothing to cancel."
        const val AGENT_NOT_WORKING = "The agent is not working."
        const val AGENT_STOPPED = "I stopped the agent."
        const val AGENT_FAILED = "The agent stopped with an error."
        const val TURN_FAILED = "The turn ended with an error."
        const val PROMPT_CANCELLED = "Cancelled. I did not send it."
        const val VOICE_OFF = "Voice off."
        const val DISTANT_VOICE = "I heard a voice far from the phone and ignored it. Speak closer, or change the voice filter in Settings."
        const val CONNECTION_LOST = "I lost the connection to Conductor. I keep trying."
        const val CONNECTION_BACK = "The connection is back."
    }
}

private sealed interface Speech {
    data class Say(val text: String) : Speech

    data class Play(val cue: Cue) : Speech
}

private enum class Interruption { TAP, VOICE }

private sealed interface ListenOutcome {
    data class Done(val heard: Heard) : ListenOutcome

    data object SpeechQueued : ListenOutcome

    data object Tapped : ListenOutcome
}

/** Counts that decide when the microphone pauses and when voice interruption turns off. */
private class TurnCounters {
    var silentListens = 0
    var failures = 0

    /** Two voice interruptions in a row that heard no words mean that the microphone hears the speaker. */
    var falseBargeIns = 0

    val isBargeInEnabled: Boolean
        get() = falseBargeIns < MAX_FALSE_BARGE_INS

    private companion object {
        const val MAX_FALSE_BARGE_INS = 2
    }
}

/** The agent's last reply, for "repeat", and the part that an interruption skipped, for "continue". */
private class ReplyMemory {
    var lastReply: List<String> = emptyList()
        private set
    private var skipped: List<Speech> = emptyList()
    private var isNewReply = true

    val hasSkipped: Boolean
        get() = skipped.isNotEmpty()

    fun restore(sentences: List<String>) {
        lastReply = sentences
    }

    fun startNew() {
        isNewReply = true
    }

    fun add(sentences: List<String>) {
        lastReply = if (isNewReply) sentences else lastReply + sentences
        isNewReply = false
    }

    fun skip(remaining: List<Speech>) {
        skipped = remaining
    }

    fun takeSkipped(): List<String> = skipped.filterIsInstance<Speech.Say>().map { it.text }.also { skipped = emptyList() }
}
