package build.conductor.android.client.ui.session

/**
 * The wait before the next transcript poll. It starts at [baseMillis], grows while polls bring nothing new,
 * grows faster after a failure, and resets when new messages arrive.
 */
class PollBackoff(
    private val baseMillis: Long = BASE_MILLIS,
    private val quietMaxMillis: Long = QUIET_MAX_MILLIS,
    private val failureMaxMillis: Long = FAILURE_MAX_MILLIS,
) {
    var currentMillis: Long = baseMillis
        private set

    fun onNewMessages() {
        currentMillis = baseMillis
    }

    fun onNoNewMessages() {
        currentMillis = (currentMillis * QUIET_GROWTH_NUMERATOR / QUIET_GROWTH_DENOMINATOR).coerceIn(baseMillis, quietMaxMillis)
    }

    fun onFailure() {
        currentMillis = (currentMillis * 2).coerceAtMost(failureMaxMillis)
    }

    private companion object {
        const val BASE_MILLIS = 3_000L
        const val QUIET_MAX_MILLIS = 15_000L
        const val FAILURE_MAX_MILLIS = 60_000L
        const val QUIET_GROWTH_NUMERATOR = 3
        const val QUIET_GROWTH_DENOMINATOR = 2
    }
}
