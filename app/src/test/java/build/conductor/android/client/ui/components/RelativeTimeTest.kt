package build.conductor.android.client.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RelativeTimeTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z")

    @Test
    fun `formats minutes, hours and days`() {
        assertEquals("just now", relativeTime("2026-10-05T11:59:30Z", now))
        assertEquals("5m ago", relativeTime("2026-10-05T11:54:59.500Z", now))
        assertEquals("3h ago", relativeTime("2026-10-05T09:00:00Z", now))
        assertEquals("2d ago", relativeTime("2026-10-03T12:00:00Z", now))
    }

    @Test
    fun `returns null for a missing or broken timestamp`() {
        assertNull(relativeTime(null, now))
        assertNull(relativeTime("yesterday", now))
    }
}
