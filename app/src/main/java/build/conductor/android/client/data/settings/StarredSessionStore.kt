package build.conductor.android.client.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import build.conductor.android.client.data.QuestionScan
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * A session that the user starred, with the last agent status that the background check saw.
 * [seenQuestionId] is the last question that a notification or the session screen showed.
 */
@Serializable
data class StarredSession(
    val title: String,
    val lastStatus: String? = null,
    val questionScan: QuestionScan = QuestionScan(),
    val seenQuestionId: String? = null,
)

class StarredSessionStore(private val dataStore: DataStore<Preferences>) : StarredSessions {
    /** Starred sessions by session id. */
    val sessions: Flow<Map<String, StarredSession>> = dataStore.data.map { decode(it[STARRED_SESSIONS]) }

    override val starredIds: Flow<Set<String>> = sessions.map { it.keys }

    override suspend fun star(sessionId: String, title: String, currentStatus: String?) = edit { sessions ->
        sessions + (sessionId to StarredSession(title, currentStatus))
    }

    override suspend fun unstar(sessionId: String) = edit { sessions -> sessions - sessionId }

    override suspend fun recordStatus(sessionId: String, status: String) = recordStatuses(mapOf(sessionId to status))

    suspend fun recordStatuses(statuses: Map<String, String>) = edit { sessions ->
        sessions.mapValues { (id, session) -> statuses[id]?.let { session.copy(lastStatus = it) } ?: session }
    }

    /** Saves where each scan stopped. An open question counts as seen, because the check notifies about it. */
    suspend fun recordQuestionScans(scans: Map<String, QuestionScan>) = edit { sessions ->
        sessions.mapValues { (id, session) ->
            scans[id]?.let { scan -> session.copy(questionScan = scan, seenQuestionId = scan.openQuestionId ?: session.seenQuestionId) } ?: session
        }
    }

    override suspend fun recordSeenQuestion(sessionId: String, toolUseId: String) = edit { sessions ->
        sessions[sessionId]?.let { sessions + (sessionId to it.copy(seenQuestionId = toolUseId)) } ?: sessions
    }

    suspend fun snapshot(): Map<String, StarredSession> = sessions.first()

    private suspend fun edit(transform: (Map<String, StarredSession>) -> Map<String, StarredSession>) {
        dataStore.edit { preferences -> preferences[STARRED_SESSIONS] = encode(transform(decode(preferences[STARRED_SESSIONS]))) }
    }

    private fun decode(text: String?): Map<String, StarredSession> =
        text?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private fun encode(sessions: Map<String, StarredSession>): String = json.encodeToString(serializer, sessions)

    private companion object {
        val STARRED_SESSIONS = stringPreferencesKey("starred_sessions")
        val serializer = MapSerializer(String.serializer(), StarredSession.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}
