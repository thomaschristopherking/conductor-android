package build.conductor.android.client.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The sessions that the user starred for notifications. */
interface StarredSessions {
    val starredIds: Flow<Set<String>>
    suspend fun setStarred(sessionId: String, title: String, isStarred: Boolean)

    object None : StarredSessions {
        override val starredIds: Flow<Set<String>> = flowOf(emptySet())
        override suspend fun setStarred(sessionId: String, title: String, isStarred: Boolean) = Unit
    }
}
