package build.conductor.android.client.notify

import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.settings.StarredSession

data class FinishedSession(val sessionId: String, val title: String, val status: AgentStatus)

/** Starred sessions whose agent was working at the last check and is now idle or in error. */
fun findFinishedSessions(starred: Map<String, StarredSession>, currentStatuses: Map<String, AgentStatus>): List<FinishedSession> =
    starred.mapNotNull { (sessionId, session) ->
        val current = currentStatuses[sessionId] ?: return@mapNotNull null
        val wasWorking = AgentStatus.from(session.lastStatus) == AgentStatus.WORKING
        if (wasWorking && current.isSettled) FinishedSession(sessionId, session.title, current) else null
    }
