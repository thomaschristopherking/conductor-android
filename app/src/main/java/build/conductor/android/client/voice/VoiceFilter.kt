package build.conductor.android.client.voice

/** How close a voice must be: [onsetMinDb] is the loudness that interrupts the app, and [marginDb] is how far a prompt must rise above the background. */
enum class VoiceFilter(val onsetMinDb: Double, val marginDb: Double) {
    ANY(onsetMinDb = -45.0, marginDb = 0.0),
    NEAR(onsetMinDb = -35.0, marginDb = 6.0),
    VERY_NEAR(onsetMinDb = -28.0, marginDb = 9.0),
    ;

    companion object {
        val DEFAULT = NEAR

        fun from(value: String?): VoiceFilter = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** Decides if the words of one listen came from a near voice, from the recognizer's loudness reports before and during the speech. */
class NearVoiceGate(private val filter: VoiceFilter) {
    private val backgroundLevels = mutableListOf<Float>()
    private var speechPeak: Float? = null
    private var isSpeaking = false

    fun onSpeechStart() {
        isSpeaking = true
    }

    fun onLevel(levelDb: Float) {
        if (isSpeaking) speechPeak = maxOf(speechPeak ?: levelDb, levelDb) else backgroundLevels += levelDb
    }

    /** True when the filter is off, or when the recognizer gave too few reports to judge, so a quiet room never blocks the user. */
    val isNear: Boolean
        get() {
            val peak = speechPeak ?: return true
            val background = median(backgroundLevels) ?: return true
            return filter.marginDb <= 0.0 || peak - background >= filter.marginDb
        }

    private fun median(levels: List<Float>): Float? = levels.sorted().let { it.getOrNull(it.size / 2) }
}
