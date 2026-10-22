package build.conductor.android.client.notify

import android.content.pm.ServiceInfo
import build.conductor.android.client.ConductorApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ConductorApplication::class)
class WorkspaceWatchServiceTest {
    @Test
    fun `the service shows its notification as a special use foreground service, and stops when nothing is subscribed`() {
        val service = Robolectric.buildService(WorkspaceWatchService::class.java).create().startCommand(0, 1).get()
        val shadow = shadowOf(service)

        assertNotNull(shadow.lastForegroundNotification)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, service.foregroundServiceType)
        assertTrue(waitUntil { shadow.isStoppedBySelf })
    }

    /** The service reads the subscriptions on a background thread. */
    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        return condition()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
    }
}
