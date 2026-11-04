package build.conductor.android.client.voice

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Decides from the loudness of each 20 ms microphone frame when a person starts to speak, above steady sound and the echo of the app's speech. */
class VoiceOnsetRule(private val minSpeechDb: Double = VoiceFilter.ANY.onsetMinDb) {
    private var quietLevelDb: Double? = null
    private val recentFrames = ArrayDeque<Boolean>()
    private var frameCount = 0

    /** Returns true when enough recent frames are loud; the first frames only measure the room. */
    fun onFrame(levelDb: Double): Boolean {
        frameCount++
        val isLoud = levelDb >= max(updateQuietLevel(levelDb) + MARGIN_DB, minSpeechDb)
        recentFrames.addLast(isLoud)
        if (recentFrames.size > WINDOW_FRAMES) recentFrames.removeFirst()
        return frameCount > WARM_UP_FRAMES && recentFrames.count { it } >= LOUD_FRAMES_NEEDED
    }

    /** The quiet level drops at once to a quieter frame, and rises slowly, so speech does not lift it. */
    private fun updateQuietLevel(levelDb: Double): Double {
        val quietLevel = quietLevelDb?.let { min(levelDb, it + QUIET_RISE_DB_PER_FRAME) } ?: levelDb
        quietLevelDb = quietLevel
        return quietLevel
    }

    companion object {
        const val FRAME_MILLIS = 20
        private const val MARGIN_DB = 15.0
        private const val QUIET_RISE_DB_PER_FRAME = 0.1
        private const val WARM_UP_FRAMES = 15
        private const val WINDOW_FRAMES = 15
        private const val LOUD_FRAMES_NEEDED = 12
        private const val SILENCE_DB = -100.0

        /** The loudness of 16-bit samples in decibels below full scale. */
        fun levelDb(samples: ShortArray, count: Int): Double {
            if (count <= 0) return SILENCE_DB
            val meanSquare = (0 until count).sumOf { samples[it].toDouble() * samples[it] } / count
            val rms = sqrt(meanSquare) / Short.MAX_VALUE
            return if (rms <= 0.0) SILENCE_DB else max(SILENCE_DB, 20 * log10(rms))
        }
    }
}
