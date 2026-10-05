package build.conductor.android.client.ui.components

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/** Formats an ISO 8601 timestamp as "just now", "5m ago", "3h ago" or "2d ago". */
fun relativeTime(isoTimestamp: String?, now: Instant = Instant.now()): String? {
    val then = isoTimestamp?.let { parseInstant(it) } ?: return null
    val elapsed = Duration.between(then, now).coerceAtLeast(Duration.ZERO)
    return when {
        elapsed.toMinutes() < 1 -> "just now"
        elapsed.toHours() < 1 -> "${elapsed.toMinutes()}m ago"
        elapsed.toDays() < 1 -> "${elapsed.toHours()}h ago"
        else -> "${elapsed.toDays()}d ago"
    }
}

private fun parseInstant(text: String): Instant? = try {
    Instant.parse(text)
} catch (_: DateTimeParseException) {
    null
}
