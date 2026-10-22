package build.conductor.android.client.data.settings

import build.conductor.android.client.data.api.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The workspaces that the user subscribed to for notifications. */
interface WorkspaceSubscriptions {
    val subscribedIds: Flow<Set<String>>

    /** [sessionStatuses] holds the status of each session now, so the background check can see the next change. */
    suspend fun subscribe(workspaceId: String, name: String, sessionStatuses: Map<String, String>)
    suspend fun unsubscribe(workspaceId: String)

    object None : WorkspaceSubscriptions {
        override val subscribedIds: Flow<Set<String>> = flowOf(emptySet())
        override suspend fun subscribe(workspaceId: String, name: String, sessionStatuses: Map<String, String>) = Unit
        override suspend fun unsubscribe(workspaceId: String) = Unit
    }
}

/** Records what the session screen showed, so the background check does not notify about it again. */
interface SessionSightings {
    /** It does nothing for a session in a workspace that the user did not subscribe to. */
    suspend fun recordStatus(status: SessionStatus)
    suspend fun recordSeenQuestion(sessionId: String, toolUseId: String)

    object None : SessionSightings {
        override suspend fun recordStatus(status: SessionStatus) = Unit
        override suspend fun recordSeenQuestion(sessionId: String, toolUseId: String) = Unit
    }
}
