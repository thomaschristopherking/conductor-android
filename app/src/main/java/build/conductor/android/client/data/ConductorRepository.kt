package build.conductor.android.client.data

import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.ArchivedWorkspace
import build.conductor.android.client.data.api.CancelledSession
import build.conductor.android.client.data.api.ConductorApi
import build.conductor.android.client.data.api.CreateSessionRequest
import build.conductor.android.client.data.api.CreateWorkspaceRequest
import build.conductor.android.client.data.api.CreatedWorkspace
import build.conductor.android.client.data.api.FavoriteModel
import build.conductor.android.client.data.api.Me
import build.conductor.android.client.data.api.Message
import build.conductor.android.client.data.api.Page
import build.conductor.android.client.data.api.Project
import build.conductor.android.client.data.api.Section
import build.conductor.android.client.data.api.RenameRequest
import build.conductor.android.client.data.api.SendMessageRequest
import build.conductor.android.client.data.api.SentMessage
import build.conductor.android.client.data.api.Session
import build.conductor.android.client.data.api.SessionStatus
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.data.api.bearer
import build.conductor.android.client.data.api.mapApiErrors
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Every call throws [ApiException] on failure. */
interface ConductorRepository {
    /** Emits each time the server rejects the saved API key. */
    val unauthorizedEvents: SharedFlow<Unit>

    suspend fun testApiKey(apiKey: String): Me
    suspend fun projects(): List<Project>
    suspend fun workspaces(projectId: String, includeArchived: Boolean, offset: Int): Page<Workspace>
    suspend fun allWorkspaces(includeArchived: Boolean): List<Workspace>
    suspend fun sections(): List<Section>
    suspend fun workspace(workspaceId: String): Workspace
    suspend fun createWorkspace(request: CreateWorkspaceRequest): CreatedWorkspace
    suspend fun renameWorkspace(workspaceId: String, name: String): Workspace
    suspend fun archiveWorkspace(workspaceId: String): ArchivedWorkspace
    suspend fun sessions(workspaceId: String): List<Session>
    suspend fun createSession(request: CreateSessionRequest): Session
    suspend fun session(sessionId: String): Session
    suspend fun sessionStatus(sessionId: String): SessionStatus
    suspend fun messagesAfter(sessionId: String, afterMessageId: String?): Page<Message>
    suspend fun sendMessage(sessionId: String, text: String, messageId: String): SentMessage
    suspend fun cancelSession(sessionId: String): CancelledSession
    suspend fun favoriteModels(): List<FavoriteModel>
}

class ApiConductorRepository(private val api: ConductorApi) : ConductorRepository {
    private val unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val unauthorizedEvents: SharedFlow<Unit> = unauthorized.asSharedFlow()

    override suspend fun testApiKey(apiKey: String): Me = mapApiErrors { api.me(bearer(apiKey)) }

    override suspend fun projects(): List<Project> = call { collectAllPages { offset -> api.projects(offset = offset) } }

    override suspend fun workspaces(projectId: String, includeArchived: Boolean, offset: Int): Page<Workspace> =
        call { api.workspaces(repo = projectId, includeArchived = includeArchived.takeIf { it }, offset = offset) }

    override suspend fun allWorkspaces(includeArchived: Boolean): List<Workspace> =
        call { collectAllPages { offset -> api.workspaces(includeArchived = includeArchived.takeIf { it }, offset = offset) } }

    override suspend fun sections(): List<Section> = call { collectAllPages { offset -> api.sections(offset = offset) } }

    override suspend fun workspace(workspaceId: String): Workspace = call { api.workspace(workspaceId) }

    override suspend fun createWorkspace(request: CreateWorkspaceRequest): CreatedWorkspace = call { api.createWorkspace(request) }

    override suspend fun renameWorkspace(workspaceId: String, name: String): Workspace =
        call { api.renameWorkspace(workspaceId, RenameRequest(name)) }

    override suspend fun archiveWorkspace(workspaceId: String): ArchivedWorkspace = call { api.archiveWorkspace(workspaceId) }

    override suspend fun sessions(workspaceId: String): List<Session> =
        call { collectAllPages { offset -> api.sessions(workspaceId, offset = offset) } }

    override suspend fun createSession(request: CreateSessionRequest): Session = call { api.createSession(request) }

    override suspend fun session(sessionId: String): Session = call { api.session(sessionId) }

    override suspend fun sessionStatus(sessionId: String): SessionStatus = call { api.sessionStatus(sessionId) }

    override suspend fun messagesAfter(sessionId: String, afterMessageId: String?): Page<Message> =
        call { api.messages(sessionId, after = afterMessageId) }

    override suspend fun sendMessage(sessionId: String, text: String, messageId: String): SentMessage =
        call { api.sendMessage(sessionId, SendMessageRequest(message = text, messageId = messageId)) }

    override suspend fun cancelSession(sessionId: String): CancelledSession = call { api.cancelSession(sessionId) }

    override suspend fun favoriteModels(): List<FavoriteModel> = call { api.favoriteModels().favoriteModels }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        mapApiErrors(block)
    } catch (exception: ApiException.Unauthorized) {
        unauthorized.tryEmit(Unit)
        throw exception
    }
}

private suspend fun <T> collectAllPages(fetchPage: suspend (offset: Int) -> Page<T>): List<T> {
    val items = mutableListOf<T>()
    do {
        val page = fetchPage(items.size)
        items += page.data
    } while (page.hasMore && page.data.isNotEmpty())
    return items
}
