package build.conductor.android.client.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import build.conductor.android.client.data.QuestionScan
import build.conductor.android.client.data.api.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What the app last saw of one session in a subscribed workspace.
 * [statusUpdatedAt] changes with each turn, so a turn that starts and ends between two checks still shows.
 * [seenQuestionId] is the last question that a notification or the session screen showed.
 */
@Serializable
data class WatchedSession(
    val status: String? = null,
    val statusUpdatedAt: String? = null,
    val questionScan: QuestionScan = QuestionScan(),
    val seenQuestionId: String? = null,
)

/** [lastActivityAt] is the activity time of the workspace at the last complete check. */
@Serializable
data class WorkspaceSubscription(
    val name: String,
    val lastActivityAt: String? = null,
    val sessions: Map<String, WatchedSession> = emptyMap(),
)

/** What one check saw of a session. [questionScan] is null when the check did not read the transcript. */
data class SessionObservation(val status: SessionStatus, val questionScan: QuestionScan? = null)

/** What one check saw of a workspace. [isComplete] is false when a status call failed, so a session can be missing. */
data class WorkspaceObservation(
    val name: String,
    val lastActivityAt: String?,
    val sessions: Map<String, SessionObservation>,
    val isComplete: Boolean,
)

class WorkspaceSubscriptionStore(private val dataStore: DataStore<Preferences>) : WorkspaceSubscriptions, SessionSightings {
    /** Subscriptions by workspace id. */
    val subscriptions: Flow<Map<String, WorkspaceSubscription>> = dataStore.data.map { decode(it[SUBSCRIPTIONS]) }

    override val subscribedIds: Flow<Set<String>> = subscriptions.map { it.keys }

    override suspend fun subscribe(workspaceId: String, name: String, sessionStatuses: Map<String, String>) = edit { subscriptions ->
        subscriptions + (workspaceId to WorkspaceSubscription(name, sessions = sessionStatuses.mapValues { WatchedSession(status = it.value) }))
    }

    override suspend fun unsubscribe(workspaceId: String) = edit { subscriptions -> subscriptions - workspaceId }

    override suspend fun recordStatus(status: SessionStatus) = editWorkspace(status.workspaceId) { it.withSessionStatus(status) }

    override suspend fun recordSeenQuestion(sessionId: String, toolUseId: String) = edit { subscriptions ->
        subscriptions.mapValues { (_, subscription) -> subscription.withSeenQuestion(sessionId, toolUseId) }
    }

    suspend fun recordObservation(workspaceId: String, observation: WorkspaceObservation) =
        editWorkspace(workspaceId) { it.withObservation(observation) }

    suspend fun snapshot(): Map<String, WorkspaceSubscription> = subscriptions.first()

    /** Does nothing when the user unsubscribed in the meantime. */
    private suspend fun editWorkspace(workspaceId: String, transform: (WorkspaceSubscription) -> WorkspaceSubscription) = edit { subscriptions ->
        subscriptions[workspaceId]?.let { subscriptions + (workspaceId to transform(it)) } ?: subscriptions
    }

    private suspend fun edit(transform: (Map<String, WorkspaceSubscription>) -> Map<String, WorkspaceSubscription>) {
        dataStore.edit { preferences -> preferences[SUBSCRIPTIONS] = encode(transform(decode(preferences[SUBSCRIPTIONS]))) }
    }

    private fun decode(text: String?): Map<String, WorkspaceSubscription> =
        text?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private fun encode(subscriptions: Map<String, WorkspaceSubscription>): String = json.encodeToString(serializer, subscriptions)

    private companion object {
        val SUBSCRIPTIONS = stringPreferencesKey("workspace_subscriptions")
        val serializer = MapSerializer(String.serializer(), WorkspaceSubscription.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** A complete check also drops the sessions that the workspace no longer lists. */
internal fun WorkspaceSubscription.withObservation(observation: WorkspaceObservation): WorkspaceSubscription {
    val observed = observation.sessions.mapValues { (id, seen) -> (sessions[id] ?: WatchedSession()).withObservation(seen) }
    val unobserved = if (observation.isComplete) emptyMap() else sessions - observed.keys
    val activity = if (observation.isComplete) observation.lastActivityAt else lastActivityAt
    return copy(name = observation.name, lastActivityAt = activity, sessions = unobserved + observed)
}

/** An open question counts as seen, because the check notifies about it. */
private fun WatchedSession.withObservation(seen: SessionObservation): WatchedSession = copy(
    status = seen.status.status,
    statusUpdatedAt = seen.status.updatedAt,
    questionScan = seen.questionScan ?: questionScan,
    seenQuestionId = seen.questionScan?.openQuestionId ?: seenQuestionId,
)

private fun WorkspaceSubscription.withSessionStatus(status: SessionStatus): WorkspaceSubscription {
    val session = sessions[status.sessionId] ?: WatchedSession()
    return copy(sessions = sessions + (status.sessionId to session.copy(status = status.status, statusUpdatedAt = status.updatedAt)))
}

private fun WorkspaceSubscription.withSeenQuestion(sessionId: String, toolUseId: String): WorkspaceSubscription {
    val session = sessions[sessionId] ?: return this
    return copy(sessions = sessions + (sessionId to session.copy(seenQuestionId = toolUseId)))
}
