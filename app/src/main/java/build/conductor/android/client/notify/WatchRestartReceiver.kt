package build.conductor.android.client.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import build.conductor.android.client.ConductorApplication
import kotlinx.coroutines.launch

/** Starts the background service again after a restart of the phone or an update of the app. */
class WatchRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTART_ACTIONS) return
        val application = context.applicationContext as ConductorApplication
        val pendingResult = goAsync()
        application.container.applicationScope.launch {
            try {
                application.watchScheduler.restartIfNeeded()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val RESTART_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
