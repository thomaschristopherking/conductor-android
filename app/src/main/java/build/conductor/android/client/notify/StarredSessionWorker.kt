package build.conductor.android.client.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import build.conductor.android.client.ApiKeyState
import build.conductor.android.client.ConductorApplication
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.api.ApiException
import kotlinx.coroutines.flow.first

/** Checks the starred sessions and notifies when an agent moves from working to idle or error. */
class StarredSessionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val container = (context.applicationContext as ConductorApplication).container

    override suspend fun doWork(): Result {
        if (container.apiKeyState.first { it != ApiKeyState.Loading } !is ApiKeyState.Present) return Result.success()
        val starred = container.starredSessionStore.snapshot()
        if (starred.isEmpty()) return Result.success()
        val statuses = fetchStatuses(container.repository, starred.keys)
        findFinishedSessions(starred, statuses).forEach { StatusNotifier.notifyFinished(applicationContext, it) }
        container.starredSessionStore.recordStatuses(statuses.mapNotNull { (id, status) -> status.apiValue?.let { id to it } }.toMap())
        return Result.success()
    }

    /** A session whose status call fails keeps its last known status until the next check. */
    private suspend fun fetchStatuses(repository: ConductorRepository, sessionIds: Set<String>): Map<String, AgentStatus> =
        sessionIds.mapNotNull { sessionId ->
            try {
                sessionId to AgentStatus.from(repository.sessionStatus(sessionId).status)
            } catch (_: ApiException) {
                null
            }
        }.filter { it.second != AgentStatus.UNKNOWN }.toMap()
}
