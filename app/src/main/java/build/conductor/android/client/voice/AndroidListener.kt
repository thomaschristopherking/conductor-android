package build.conductor.android.client.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Listens with the phone's speech recognizer. Android requires every call on the main thread. */
class AndroidListener(context: Context, private val filter: VoiceFilter) : Listener {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    override suspend fun listen(progress: HearingProgress): Heard = suspendCancellableCoroutine { continuation ->
        val recognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer = it }
        recognizer.setRecognitionListener(
            RecognitionCallbacks(progress, NearVoiceGate(filter)) { heard ->
                if (heard is Heard.Failure && !heard.isFatal) recreate()
                if (continuation.isActive) continuation.resume(heard)
            },
        )
        recognizer.startListening(recognizeIntent())
        // The conversation cancels on the main thread, so this handler runs before the next call starts the microphone.
        continuation.invokeOnCancellation { recognizer.cancel() }
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    /** A busy or broken recognizer often recovers only as a new instance. */
    private fun recreate() = release()

    private fun recognizeIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)

    companion object {
        /** True when the phone has a speech recognizer that the app can use. */
        fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
    }
}

private class RecognitionCallbacks(
    private val progress: HearingProgress,
    private val gate: NearVoiceGate,
    private val onHeard: (Heard) -> Unit,
) : RecognitionListener {
    override fun onBeginningOfSpeech() {
        gate.onSpeechStart()
        progress.onSpeechStart()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        bestText(partialResults)?.let(progress::onPartialWords)
    }

    override fun onResults(results: Bundle?) = onHeard(heardFrom(bestText(results)))

    override fun onError(error: Int) = onHeard(heardFromError(error))

    override fun onReadyForSpeech(params: Bundle?) = Unit

    override fun onRmsChanged(rmsdB: Float) = gate.onLevel(rmsdB)

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun heardFrom(text: String?): Heard = when {
        text == null -> Heard.Silence
        gate.isNear -> Heard.Words(text)
        else -> Heard.Distant
    }

    private fun bestText(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

private fun heardFromError(error: Int): Heard = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Heard.Silence
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Heard.Failure("Voice mode needs the microphone permission.", isFatal = true)
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        Heard.Failure("The speech recognizer does not support the language of the phone.", isFatal = true)
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
        Heard.Failure("Speech recognition needs a connection.", isFatal = false)
    SpeechRecognizer.ERROR_AUDIO -> Heard.Failure("The microphone did not work.", isFatal = false)
    else -> Heard.Failure("Speech recognition failed with code $error.", isFatal = false)
}
