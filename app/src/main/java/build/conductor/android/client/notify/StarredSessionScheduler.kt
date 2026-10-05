package build.conductor.android.client.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import build.conductor.android.client.data.settings.StarredSessionStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** Runs the background check every 15 minutes while at least one session is starred. */
class StarredSessionScheduler(private val context: Context, private val store: StarredSessionStore) {
    suspend fun followStarredSessions() {
        store.starredIds.map { it.isNotEmpty() }.distinctUntilChanged().collect { hasStarred ->
            if (hasStarred) schedule() else cancel()
        }
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<StarredSessionWorker>(CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "starred-session-check"
        const val CHECK_INTERVAL_MINUTES = 15L
    }
}
