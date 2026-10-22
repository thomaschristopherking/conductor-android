package build.conductor.android.client.notify

import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.settings.SessionObservation
import build.conductor.android.client.data.settings.WatchedSession

/** Why the app notifies about a session. */
enum class AgentAlert { FINISHED, FAILED, ASKED }

/** The alert for each observed session, by session id. A session that the app sees for the first time gets no alert. */
fun findAlerts(saved: Map<String, WatchedSession>, observed: Map<String, SessionObservation>): Map<String, AgentAlert> =
    observed.mapNotNull { (sessionId, seen) ->
        saved[sessionId]?.let { previous -> alertFor(previous, seen) }?.let { sessionId to it }
    }.toMap()

private fun alertFor(previous: WatchedSession, seen: SessionObservation): AgentAlert? {
    val current = AgentStatus.from(seen.status.status)
    return when {
        hasNewQuestion(previous, seen) -> AgentAlert.ASKED
        !current.isSettled || !hasFinishedTurn(previous, seen) -> null
        current == AgentStatus.ERROR -> AgentAlert.FAILED
        else -> AgentAlert.FINISHED
    }
}

private fun hasNewQuestion(previous: WatchedSession, seen: SessionObservation): Boolean {
    val openQuestionId = seen.questionScan?.openQuestionId ?: return false
    return openQuestionId != previous.seenQuestionId
}

/** A new status time on a settled session means that a whole turn ran between two checks. */
private fun hasFinishedTurn(previous: WatchedSession, seen: SessionObservation): Boolean =
    AgentStatus.from(previous.status) == AgentStatus.WORKING ||
        (previous.statusUpdatedAt != null && previous.statusUpdatedAt != seen.status.updatedAt)
