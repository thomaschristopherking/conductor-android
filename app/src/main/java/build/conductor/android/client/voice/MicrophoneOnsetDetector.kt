package build.conductor.android.client.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Hears the user start to speak while the app speaks; the voice-call source turns on the echo canceller, which removes most of the app's voice. */
class MicrophoneOnsetDetector(context: Context, private val filter: VoiceFilter) : VoiceOnsetDetector {
    private val appContext = context.applicationContext

    override suspend fun awaitVoice() {
        val record = openRecord() ?: awaitCancellation()
        val effects = listOfNotNull(echoCanceller(record.audioSessionId), noiseSuppressor(record.audioSessionId), gainControlOff(record.audioSessionId))
        try {
            withContext(Dispatchers.IO) { readUntilVoice(record) }
        } finally {
            effects.forEach(AudioEffect::release)
            record.release()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRecord(): AudioRecord? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return null
        val bufferSize = maxOf(AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING), FRAME_SAMPLES * 2 * BUFFER_FRAMES)
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize)
        return record.takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: run { record.release(); null }
    }

    private suspend fun readUntilVoice(record: AudioRecord) {
        val rule = VoiceOnsetRule(filter.onsetMinDb)
        val frame = ShortArray(FRAME_SAMPLES)
        record.startRecording()
        while (true) {
            coroutineContext.ensureActive()
            val count = record.read(frame, 0, FRAME_SAMPLES)
            if (count < 0) awaitCancellation()
            if (rule.onFrame(VoiceOnsetRule.levelDb(frame, count))) return
        }
    }

    private fun echoCanceller(sessionId: Int): AudioEffect? =
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId)?.apply { enabled = true } else null

    private fun noiseSuppressor(sessionId: Int): AudioEffect? =
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId)?.apply { enabled = true } else null

    /** Automatic gain makes a far voice as loud as a near one, so the voice filter turns it off. */
    private fun gainControlOff(sessionId: Int): AudioEffect? =
        if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId)?.apply { enabled = false } else null

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SAMPLES = SAMPLE_RATE * VoiceOnsetRule.FRAME_MILLIS / 1_000
        const val BUFFER_FRAMES = 10
    }
}
