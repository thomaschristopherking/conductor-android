package build.conductor.android.client.notify

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Runs the background service and a periodic check while the user has subscriptions and an API key.
 * The periodic check catches changes when Android stops the service.
 */
class WatchScheduler(private val context: Context, private val shouldWatch: Flow<Boolean>) {
    suspend fun followSubscriptions() {
        removeStarredSessions()
        shouldWatch.distinctUntilChanged().collect { isWatching ->
            if (isWatching) startWatching() else cancelPeriodicCheck()
        }
    }

    /** Android stops the service sometimes, and lets the app start it again only from the screen or after a restart of the phone. */
    suspend fun restartIfNeeded() {
        if (shouldWatch.first()) WorkspaceWatchService.start(context)
    }

    private fun startWatching() {
        val request = PeriodicWorkRequestBuilder<WorkspaceCheckWorker>(CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        WorkspaceWatchService.start(context)
    }

    private fun cancelPeriodicCheck() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Version 0.1.0 build 9 and earlier notified about starred sessions; workspace subscriptions replace them. */
    private fun removeStarredSessions() {
        WorkManager.getInstance(context).cancelUniqueWork(STARRED_SESSIONS_WORK_NAME)
        context.preferencesDataStoreFile(STARRED_SESSIONS_FILE).delete()
    }

    private companion object {
        const val WORK_NAME = "workspace-check"
        const val CHECK_INTERVAL_MINUTES = 15L
        const val STARRED_SESSIONS_WORK_NAME = "starred-session-check"
        const val STARRED_SESSIONS_FILE = "starred_sessions"
    }
}
