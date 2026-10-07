package build.conductor.android.client.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import build.conductor.android.client.ApiKeyState
import build.conductor.android.client.ConductorApplication
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.QuestionScan
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.settings.StarredSession
import kotlinx.coroutines.flow.first

/** Checks the starred sessions and notifies when an agent moves from working to idle or error, or asks a question. */
class StarredSessionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val container = (context.applicationContext as ConductorApplication).container

    override suspend fun doWork(): Result {
        if (container.apiKeyState.first { it != ApiKeyState.Loading } !is ApiKeyState.Present) return Result.success()
        val starred = container.starredSessionStore.snapshot()
        if (starred.isEmpty()) return Result.success()
        val statuses = fetchStatuses(container.repository, starred.keys)
        val scans = scanWorkingSessions(starred.filterKeys { statuses[it] == AgentStatus.WORKING })
        findFinishedSessions(starred, statuses).forEach { StatusNotifier.notifyFinished(applicationContext, it) }
        findNewQuestions(starred, scans).forEach { StatusNotifier.notifyQuestion(applicationContext, it) }
        container.starredSessionStore.recordStatuses(statuses.mapNotNull { (id, status) -> status.apiValue?.let { id to it } }.toMap())
        container.starredSessionStore.recordQuestionScans(scans)
        return Result.success()
    }

    /** A session whose scan fails keeps its saved scan until the next check. */
    private suspend fun scanWorkingSessions(working: Map<String, StarredSession>): Map<String, QuestionScan> =
        working.mapNotNull { (sessionId, session) ->
            try {
                sessionId to container.questionScanner.scan(sessionId, session.questionScan)
            } catch (_: ApiException) {
                null
            }
        }.toMap()

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
