package build.conductor.android.client.voice

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Records what the app says; each sentence takes [SAY_MILLIS] of virtual time. */
class FakeSpeaker : Speaker {
    val events = mutableListOf<String>()
    var isEngineReady = true

    val sentences: List<String>
        get() = events.filter { !it.startsWith(CUE_PREFIX) }

    override suspend fun isReady(): Boolean = isEngineReady

    override suspend fun say(text: String) {
        events += text
        delay(SAY_MILLIS)
    }

    override suspend fun play(cue: Cue) {
        events += "$CUE_PREFIX$cue"
        delay(CUE_MILLIS)
    }

    companion object {
        const val SAY_MILLIS = 1_000L
        const val CUE_MILLIS = 100L
        const val CUE_PREFIX = "cue:"
    }
}

/** What the user does during one listen. */
sealed interface ListenScript {
    /** The user starts to speak after [startMillis] and the recognizer returns [text] after [endMillis]. */
    data class Speak(val text: String, val startMillis: Long = 200, val endMillis: Long = 1_000) : ListenScript

    /** The recognizer understands the words, but the voice filter finds them too far. */
    data class SpeakFar(val text: String) : ListenScript

    data class Fail(val failure: Heard.Failure) : ListenScript
}

/**
 * The user speaks when the test enqueues a step: an open listen takes it at once, or the next listen does.
 * A listen with no step reports silence after [SILENCE_MILLIS], as Google's recognizer does after about five seconds.
 */
class FakeListener : Listener {
    private val script = Channel<ListenScript>(Channel.UNLIMITED)
    var listenCount = 0
        private set

    fun enqueue(vararg steps: ListenScript) {
        steps.forEach { script.trySend(it) }
    }

    override suspend fun listen(progress: HearingProgress): Heard {
        listenCount++
        return when (val step = withTimeoutOrNull(SILENCE_MILLIS) { script.receive() }) {
            null -> Heard.Silence
            is ListenScript.Speak -> speak(step, progress)
            is ListenScript.SpeakFar -> speak(ListenScript.Speak(step.text), progress).let { Heard.Distant }
            is ListenScript.Fail -> step.failure
        }
    }

    private suspend fun speak(step: ListenScript.Speak, progress: HearingProgress): Heard {
        delay(step.startMillis)
        progress.onSpeechStart()
        delay(step.endMillis - step.startMillis)
        return Heard.Words(step.text)
    }

    companion object {
        const val SILENCE_MILLIS = 5_000L
    }
}

/** Reports the user's voice when the test sends it; [isWaiting] shows if the app listens for an interruption. */
class FakeOnsetDetector : VoiceOnsetDetector {
    private val voices = Channel<Unit>(Channel.UNLIMITED)
    var isWaiting = false
        private set

    fun speakOver() {
        voices.trySend(Unit)
    }

    override suspend fun awaitVoice() {
        isWaiting = true
        try {
            voices.receive()
        } finally {
            isWaiting = false
        }
    }
}

/** Never hears the user, like a phone with no usable microphone. */
object DeafOnsetDetector : VoiceOnsetDetector {
    override suspend fun awaitVoice() = awaitCancellation()
}
