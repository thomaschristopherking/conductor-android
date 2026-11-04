package build.conductor.android.client.voice

/** A short tone that tells the user what happened without words. */
enum class Cue {
    /** The microphone is open. */
    YOUR_TURN,

    /** The app sent the prompt to the agent. */
    SENT,

    /** The agent finished its turn. */
    AGENT_DONE,
}

/** Speaks on the phone's speaker or headset. A cancelled call stops the sound at once. */
interface Speaker {
    /** False when the phone has no text-to-speech engine. */
    suspend fun isReady(): Boolean

    /** Returns when the sentence ends. */
    suspend fun say(text: String)

    suspend fun play(cue: Cue)
}

sealed interface Heard {
    data class Words(val text: String) : Heard

    /** The user said nothing, or nothing that the recognizer understood. */
    data object Silence : Heard

    /** The recognizer understood words, but the voice was too far from the phone for the voice filter. */
    data object Distant : Heard

    /** [isFatal] is true when listening cannot work again, for example without the microphone permission. */
    data class Failure(val message: String, val isFatal: Boolean) : Heard
}

/** What the recognizer reports while the user speaks. */
interface HearingProgress {
    fun onSpeechStart()

    fun onPartialWords(text: String)
}

/** Turns one utterance into text. A cancelled call closes the microphone. */
fun interface Listener {
    suspend fun listen(progress: HearingProgress): Heard
}

/** Returns when the user starts to speak over the app; a phone with no usable microphone never returns. */
fun interface VoiceOnsetDetector {
    suspend fun awaitVoice()
}

class VoiceIo(val speaker: Speaker, val listener: Listener, val onsetDetector: VoiceOnsetDetector)
