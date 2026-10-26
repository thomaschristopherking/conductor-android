package build.conductor.android.client.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import build.conductor.android.client.ConductorApplication

/** Shows the confirmation dialog of Android, and reports a failed or cancelled install to the [AppUpdater]. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = (context.applicationContext as ConductorApplication).container.appUpdater
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> confirmationIntent(intent)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater.onInstallCancelled()
            PackageInstaller.STATUS_FAILURE_CONFLICT -> updater.onInstallFailed(KEY_CONFLICT_MESSAGE)
            else -> updater.onInstallFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Android did not install the update (status $status).")
        }
    }

    private fun confirmationIntent(intent: Intent): Intent? = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)

    private companion object {
        const val KEY_CONFLICT_MESSAGE = "A different key signs the installed app. Uninstall the app, then install the new build from the Releases page on GitHub."
    }
}
