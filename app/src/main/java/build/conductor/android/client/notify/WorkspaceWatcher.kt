package build.conductor.android.client.notify

import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.QuestionScan
import build.conductor.android.client.data.QuestionScanner
import build.conductor.android.client.data.WorkspaceState
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Session
import build.conductor.android.client.data.api.SessionStatus
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.data.settings.SessionObservation
import build.conductor.android.client.data.settings.WorkspaceObservation
import build.conductor.android.client.data.settings.WorkspaceSubscription
import build.conductor.android.client.data.settings.WorkspaceSubscriptionStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** A notification about one session. */
data class SessionAlert(val workspaceId: String, val workspaceName: String, val sessionId: String, val sessionTitle: String, val alert: AgentAlert)

fun interface AlertNotifier {
    fun notify(alert: SessionAlert)
}

/**
 * Checks the subscribed workspaces and notifies when an agent finishes a turn or asks a question.
 * One call reads each workspace; the sessions of a workspace are read only when its activity time changed or an agent worked.
 */
class WorkspaceWatcher(
    private val repository: ConductorRepository,
    private val questionScanner: QuestionScanner,
    private val store: WorkspaceSubscriptionStore,
    private val notifier: AlertNotifier,
) {
    private val checkLock = Mutex()
    private val callPermits = Semaphore(MAX_PARALLEL_CALLS)

    /** Returns true while an agent in a subscribed workspace works. The background service and the periodic check share one lock. */
    suspend fun check(): Boolean = checkLock.withLock {
        coroutineScope {
            store.snapshot().map { (workspaceId, subscription) -> async { checkWorkspace(workspaceId, subscription) } }.awaitAll().any { it }
        }
    }

    /** A failed call keeps the saved state until the next check. */
    private suspend fun checkWorkspace(workspaceId: String, subscription: WorkspaceSubscription): Boolean = try {
        checkReadableWorkspace(callPermits.withPermit { repository.workspace(workspaceId) }, subscription)
    } catch (exception: ApiException) {
        if (exception.isNotFound) store.unsubscribe(workspaceId)
        subscription.hasWorkingSession
    }

    private suspend fun checkReadableWorkspace(workspace: Workspace, subscription: WorkspaceSubscription): Boolean {
        if (workspace.isClosed) {
            store.unsubscribe(workspace.id)
            return false
        }
        if (!needsSessionCheck(workspace, subscription)) return false
        val sessions = callPermits.withPermit { repository.sessions(workspace.id) }
        val observation = observe(workspace, sessions, subscription)
        notifyAlerts(workspace, sessions, findAlerts(subscription.sessions, observation.sessions))
        store.recordObservation(workspace.id, observation)
        return observation.sessions.values.any { AgentStatus.from(it.status.status) == AgentStatus.WORKING }
    }

    private suspend fun observe(workspace: Workspace, sessions: List<Session>, subscription: WorkspaceSubscription): WorkspaceObservation {
        val statuses = fetchStatuses(sessions)
        val scans = scanWorkingSessions(statuses.values.filter { AgentStatus.from(it.status) == AgentStatus.WORKING }, subscription)
        val observed = statuses.mapValues { (sessionId, status) -> SessionObservation(status, scans[sessionId]) }
        return WorkspaceObservation(workspace.name, workspace.lastActivityAt, observed, isComplete = statuses.size == sessions.size)
    }

    /** A session whose status call fails is left out. */
    private suspend fun fetchStatuses(sessions: List<Session>): Map<String, SessionStatus> = coroutineScope {
        sessions.map { session -> async { readOrNull { repository.sessionStatus(session.id) } } }.awaitAll().filterNotNull().associateBy { it.sessionId }
    }

    /** The status says "working" while a question waits, so only the transcript shows the question. */
    private suspend fun scanWorkingSessions(working: List<SessionStatus>, subscription: WorkspaceSubscription): Map<String, QuestionScan> = coroutineScope {
        working.map { status ->
            async { readOrNull { status.sessionId to questionScanner.scan(status.sessionId, subscription.sessions[status.sessionId]?.questionScan ?: QuestionScan()) } }
        }.awaitAll().filterNotNull().toMap()
    }

    private fun notifyAlerts(workspace: Workspace, sessions: List<Session>, alerts: Map<String, AgentAlert>) {
        val titles = sessions.associate { it.id to (it.name ?: UNTITLED_SESSION) }
        alerts.forEach { (sessionId, alert) -> notifier.notify(SessionAlert(workspace.id, workspace.name, sessionId, titles[sessionId] ?: UNTITLED_SESSION, alert)) }
    }

    private suspend fun <T> readOrNull(read: suspend () -> T): T? = try {
        callPermits.withPermit { read() }
    } catch (_: ApiException) {
        null
    }

    private companion object {
        const val MAX_PARALLEL_CALLS = 6
        const val UNTITLED_SESSION = "Untitled session"
    }
}

/** Reads the sessions only when an agent did something since the last complete check, or still works. */
private fun needsSessionCheck(workspace: Workspace, subscription: WorkspaceSubscription): Boolean =
    workspace.lastActivityAt == null || workspace.lastActivityAt != subscription.lastActivityAt || subscription.hasWorkingSession

private val WorkspaceSubscription.hasWorkingSession: Boolean
    get() = sessions.values.any { AgentStatus.from(it.status) == AgentStatus.WORKING }

private val Workspace.isClosed: Boolean
    get() = WorkspaceState.from(state).let { it == WorkspaceState.ARCHIVED || it == WorkspaceState.DELETED }

private val ApiException.isNotFound: Boolean
    get() = this is ApiException.Client && statusCode == HTTP_NOT_FOUND

private const val HTTP_NOT_FOUND = 404
