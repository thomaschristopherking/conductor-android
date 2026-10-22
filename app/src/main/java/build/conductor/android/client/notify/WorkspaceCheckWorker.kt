package build.conductor.android.client.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import build.conductor.android.client.ApiKeyState
import build.conductor.android.client.ConductorApplication
import kotlinx.coroutines.flow.first

/** Checks the subscribed workspaces every 15 minutes, also when Android stopped the background service. */
class WorkspaceCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val container = (context.applicationContext as ConductorApplication).container

    override suspend fun doWork(): Result {
        if (container.apiKeyState.first { it != ApiKeyState.Loading } is ApiKeyState.Present) container.workspaceWatcher.check()
        return Result.success()
    }
}
