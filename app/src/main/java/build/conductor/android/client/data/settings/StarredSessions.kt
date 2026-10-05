package build.conductor.android.client.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The sessions that the user starred for notifications. */
interface StarredSessions {
    val starredIds: Flow<Set<String>>

    /** [currentStatus] is the API status value now, so the background check can see the next change. */
    suspend fun star(sessionId: String, title: String, currentStatus: String?)
    suspend fun unstar(sessionId: String)

    object None : StarredSessions {
        override val starredIds: Flow<Set<String>> = flowOf(emptySet())
        override suspend fun star(sessionId: String, title: String, currentStatus: String?) = Unit
        override suspend fun unstar(sessionId: String) = Unit
    }
}
