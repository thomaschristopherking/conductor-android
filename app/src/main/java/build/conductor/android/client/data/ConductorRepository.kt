package build.conductor.android.client.data

import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.ArchivedWorkspace
import build.conductor.android.client.data.api.CancelledSession
import build.conductor.android.client.data.api.ConductorApi
import build.conductor.android.client.data.api.CreateSessionRequest
import build.conductor.android.client.data.api.CreateWorkspaceRequest
import build.conductor.android.client.data.api.CreatedWorkspace
import build.conductor.android.client.data.api.FavoriteModel
import build.conductor.android.client.data.api.HedgePolicy
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
import build.conductor.android.client.data.api.hedge
import build.conductor.android.client.data.api.mapApiErrors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
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
    /** Every message after [afterMessageId], or the whole transcript when it is null. */
    suspend fun newMessages(sessionId: String, afterMessageId: String?): List<Message>
    suspend fun sendMessage(sessionId: String, text: String, messageId: String): SentMessage
    suspend fun cancelSession(sessionId: String): CancelledSession
    suspend fun favoriteModels(): List<FavoriteModel>
}

/** Every read is hedged; a write is sent once, because the API does not make writes idempotent. */
class ApiConductorRepository(
    private val api: ConductorApi,
    private val hedgePolicy: HedgePolicy = HedgePolicy.DEFAULT,
) : ConductorRepository {
    private val unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val unauthorizedEvents: SharedFlow<Unit> = unauthorized.asSharedFlow()

    override suspend fun testApiKey(apiKey: String): Me = mapApiErrors { hedge { api.me(bearer(apiKey)) } }

    override suspend fun projects(): List<Project> = call { collectAllPages { offset -> hedge { api.projects(offset = offset) } } }

    override suspend fun workspaces(projectId: String, includeArchived: Boolean, offset: Int): Page<Workspace> =
        read { api.workspaces(repo = projectId, includeArchived = includeArchived.takeIf { it }, offset = offset) }

    override suspend fun allWorkspaces(includeArchived: Boolean): List<Workspace> =
        call { collectAllPages { offset -> hedge { api.workspaces(includeArchived = includeArchived.takeIf { it }, offset = offset) } } }

    override suspend fun sections(): List<Section> = call { collectAllPages { offset -> hedge { api.sections(offset = offset) } } }

    override suspend fun workspace(workspaceId: String): Workspace = read { api.workspace(workspaceId) }

    override suspend fun createWorkspace(request: CreateWorkspaceRequest): CreatedWorkspace = call { api.createWorkspace(request) }

    override suspend fun renameWorkspace(workspaceId: String, name: String): Workspace =
        call { api.renameWorkspace(workspaceId, RenameRequest(name)) }

    override suspend fun archiveWorkspace(workspaceId: String): ArchivedWorkspace = call { api.archiveWorkspace(workspaceId) }

    override suspend fun sessions(workspaceId: String): List<Session> =
        call { collectAllPages { offset -> hedge { api.sessions(workspaceId, offset = offset) } } }

    override suspend fun createSession(request: CreateSessionRequest): Session = call { api.createSession(request) }

    override suspend fun session(sessionId: String): Session = read { api.session(sessionId) }

    override suspend fun sessionStatus(sessionId: String): SessionStatus = read { api.sessionStatus(sessionId) }

    override suspend fun newMessages(sessionId: String, afterMessageId: String?): List<Message> = call {
        if (afterMessageId == null) {
            collectPagesInWaves { offset -> hedge { api.messages(sessionId, offset = offset) } }
        } else {
            collectPagesAfter(afterMessageId) { after -> hedge { api.messages(sessionId, after = after) } }
        }
    }

    override suspend fun sendMessage(sessionId: String, text: String, messageId: String): SentMessage =
        call { api.sendMessage(sessionId, SendMessageRequest(message = text, messageId = messageId)) }

    override suspend fun cancelSession(sessionId: String): CancelledSession = call { api.cancelSession(sessionId) }

    override suspend fun favoriteModels(): List<FavoriteModel> = read { api.favoriteModels().favoriteModels }

    private suspend fun <T> read(request: suspend () -> T): T = call { hedge(request) }

    private suspend fun <T> hedge(request: suspend () -> T): T = hedgePolicy.hedge(request)

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

/** Each page needs the last id of the page before it, so this reads one page at a time. */
private suspend fun collectPagesAfter(afterMessageId: String, fetchPage: suspend (after: String) -> Page<Message>): List<Message> {
    val messages = mutableListOf<Message>()
    do {
        val page = fetchPage(messages.lastOrNull()?.id ?: afterMessageId)
        messages += page.data
    } while (page.hasMore && page.data.isNotEmpty())
    return messages
}

/** Reads the first page alone, then [PAGES_PER_WAVE] pages at a time, because each page is a full round trip. */
private suspend fun <T> collectPagesInWaves(fetchPage: suspend (offset: Int) -> Page<T>): List<T> {
    val items = mutableListOf<T>()
    var waveSize = 1
    do {
        val wave = fetchWave(items.size, waveSize, fetchPage)
        waveSize = PAGES_PER_WAVE
    } while (appendContiguousPages(items, wave))
    return items
}

private suspend fun <T> fetchWave(start: Int, size: Int, fetchPage: suspend (offset: Int) -> Page<T>): List<Page<T>> = coroutineScope {
    List(size) { index -> async { fetchPage(start + index * ConductorApi.PAGE_SIZE) } }.awaitAll()
}

/** Adds pages in order and returns true when more pages follow; a short page ends the wave, so the next wave starts at the right offset. */
private fun <T> appendContiguousPages(items: MutableList<T>, wave: List<Page<T>>): Boolean {
    for (page in wave) {
        items += page.data
        if (!page.hasMore || page.data.isEmpty()) return false
        if (page.data.size < ConductorApi.PAGE_SIZE) return true
    }
    return true
}

private const val PAGES_PER_WAVE = 8
