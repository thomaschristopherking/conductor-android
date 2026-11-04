package build.conductor.android.client.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Speaks with the phone's text-to-speech engine, and lowers the volume of music while it speaks. */
class AndroidSpeaker(context: Context) : Speaker {
    private val ready = CompletableDeferred<Boolean>()
    private val finished = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val textToSpeech = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
    private val tones = ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
    private val focus = SpeechFocus(context.applicationContext.getSystemService(AudioManager::class.java))

    init {
        textToSpeech.setAudioAttributes(SPEECH_ATTRIBUTES)
        textToSpeech.setOnUtteranceProgressListener(CompletionListener(finished))
    }

    override suspend fun isReady(): Boolean = ready.await()

    override suspend fun say(text: String) {
        if (!ready.await()) return
        val utteranceId = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>().also { finished[utteranceId] = it }
        focus.hold()
        try {
            if (textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) return
            done.await()
        } finally {
            finished.remove(utteranceId)
            if (!done.isCompleted) textToSpeech.stop()
            focus.releaseSoon()
        }
    }

    override suspend fun play(cue: Cue) {
        val (tone, millis) = TONES.getValue(cue)
        tones.startTone(tone, millis)
        delay(millis + TONE_GAP_MILLIS)
    }

    fun release() {
        textToSpeech.shutdown()
        tones.release()
        focus.releaseNow()
    }

    private companion object {
        const val TONE_VOLUME = 60
        const val TONE_GAP_MILLIS = 80L
        val TONES = mapOf(
            Cue.YOUR_TURN to (ToneGenerator.TONE_PROP_BEEP to 120),
            Cue.SENT to (ToneGenerator.TONE_PROP_ACK to 200),
            Cue.AGENT_DONE to (ToneGenerator.TONE_PROP_BEEP2 to 250),
        )
    }
}

private val SPEECH_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/** Completes the wait of each sentence; the engine calls back on its own thread. */
private class CompletionListener(private val finished: Map<String, CompletableDeferred<Unit>>) : UtteranceProgressListener() {
    override fun onStart(utteranceId: String?) = Unit

    override fun onDone(utteranceId: String?) = complete(utteranceId)

    override fun onStop(utteranceId: String?, interrupted: Boolean) = complete(utteranceId)

    @Deprecated("Deprecated in Java")
    override fun onError(utteranceId: String?) = complete(utteranceId)

    override fun onError(utteranceId: String?, errorCode: Int) = complete(utteranceId)

    private fun complete(utteranceId: String?) {
        finished[utteranceId]?.complete(Unit)
    }
}

/** Keeps audio focus between sentences, so music does not jump up and down in the gaps. */
private class SpeechFocus(private val audioManager: AudioManager) {
    private val handler = Handler(Looper.getMainLooper())
    private val abandon = Runnable { releaseNow() }
    private var isHeld = false
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(SPEECH_ATTRIBUTES).build()

    fun hold() {
        handler.removeCallbacks(abandon)
        if (isHeld) return
        audioManager.requestAudioFocus(request)
        isHeld = true
    }

    fun releaseSoon() {
        handler.removeCallbacks(abandon)
        handler.postDelayed(abandon, RELEASE_DELAY_MILLIS)
    }

    fun releaseNow() {
        handler.removeCallbacks(abandon)
        if (isHeld) audioManager.abandonAudioFocusRequest(request)
        isHeld = false
    }

    private companion object {
        const val RELEASE_DELAY_MILLIS = 1_500L
    }
}
