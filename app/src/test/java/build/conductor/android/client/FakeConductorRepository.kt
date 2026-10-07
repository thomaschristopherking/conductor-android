package build.conductor.android.client

import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.ArchivedWorkspace
import build.conductor.android.client.data.api.CancelledSession
import build.conductor.android.client.data.api.CreateSessionRequest
import build.conductor.android.client.data.api.CreateWorkspaceRequest
import build.conductor.android.client.data.api.CreatedWorkspace
import build.conductor.android.client.data.api.FavoriteModel
import build.conductor.android.client.data.api.Me
import build.conductor.android.client.data.api.Message
import build.conductor.android.client.data.api.Page
import build.conductor.android.client.data.api.Project
import build.conductor.android.client.data.api.SentMessage
import build.conductor.android.client.data.api.Session
import build.conductor.android.client.data.api.SessionStatus
import build.conductor.android.client.data.api.Workspace
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json

/** An in-memory Conductor that behaves like the live API, including the `after` cursor. */
class FakeConductorRepository : ConductorRepository {
    override val unauthorizedEvents: SharedFlow<Unit> = MutableSharedFlow()

    var sessionStatusValue = "idle"
    val transcript = mutableListOf<Message>()
    val messageCursors = mutableListOf<String?>()
    val sentMessages = mutableListOf<Pair<String, String>>()
    val createdWorkspaces = mutableListOf<CreateWorkspaceRequest>()
    val createdSessions = mutableListOf<CreateSessionRequest>()
    var cancelCount = 0
    var canceledQueuedMessages = 0
    val callOrder = mutableListOf<String>()
    var messagesFailure: ApiException? = null
    var sendFailure: ApiException? = null
    var projectsResult: () -> List<Project> = { emptyList() }
    var workspacePages: (offset: Int, includeArchived: Boolean) -> Page<Workspace> = { _, _ -> Page(emptyList()) }
    val workspaceRequests = mutableListOf<Pair<Int, Boolean>>()
    val archivedWorkspaceIds = mutableListOf<String>()
    var archiveFailure: ApiException? = null
    var favorites = listOf(FavoriteModel("codex", "gpt-6.1-sol", "high"))
    var sessionsInWorkspace = listOf<Session>()
    var sessionStatuses = mutableMapOf<String, String>()

    fun addUserMessage(text: String, clientId: String) = addMessage("userMessage", """{"type":"userMessage","id":"$clientId","message":"$text","state":"sent"}""")

    fun addAgentText(text: String) =
        addMessage("agent", """{"type":"agent","rawPayload":{"type":"assistant","message":{"content":[{"type":"text","text":"$text"}]}}}""")

    fun addQuestion(toolUseId: String) = addMessage(
        "agent",
        """{"type":"agent","rawPayload":{"type":"assistant","message":{"content":[{"type":"tool_use","id":"$toolUseId","name":"mcp__conductor__AskUserQuestion",""" +
            """"input":{"questions":[{"question":"Which colour?","options":["Red","Green"]},{"question":"Which sizes?","multiSelect":true,"options":["Small","Large"]}]}}]}}}""",
    )

    fun addToolResult(toolUseId: String, text: String) = addMessage(
        "agent",
        """{"type":"agent","rawPayload":{"type":"user","message":{"content":[{"type":"tool_result","tool_use_id":"$toolUseId","content":[{"type":"text","text":"$text"}]}]}}}""",
    )

    fun addTurnEnd() = addMessage("agent", """{"type":"agent","rawPayload":{"type":"result","subtype":"success","is_error":false}}""")

    private fun addMessage(type: String, content: String) {
        val index = transcript.size + 1
        transcript += Message("m$index", SESSION_ID, index.toLong(), type, Json.parseToJsonElement(content), "2026-10-05T06:00:00Z")
    }

    override suspend fun testApiKey(apiKey: String): Me =
        if (apiKey == VALID_KEY) Me(userId = "u1", name = "Test User") else throw ApiException.Unauthorized("Unauthorized client request")

    override suspend fun projects(): List<Project> = projectsResult()

    override suspend fun workspaces(projectId: String, includeArchived: Boolean, offset: Int): Page<Workspace> {
        workspaceRequests += offset to includeArchived
        return workspacePages(offset, includeArchived)
    }

    override suspend fun workspace(workspaceId: String) = workspace(workspaceId, "Workspace $workspaceId")

    override suspend fun createWorkspace(request: CreateWorkspaceRequest): CreatedWorkspace {
        createdWorkspaces += request
        return CreatedWorkspace("w-new", "s-new", "conductor://workspace?id=w-new")
    }

    override suspend fun renameWorkspace(workspaceId: String, name: String) = workspace(workspaceId, name)

    override suspend fun archiveWorkspace(workspaceId: String): ArchivedWorkspace {
        archiveFailure?.let { throw it }
        archivedWorkspaceIds += workspaceId
        return ArchivedWorkspace(workspaceId, "archived")
    }

    override suspend fun sessions(workspaceId: String): List<Session> = sessionsInWorkspace

    override suspend fun createSession(request: CreateSessionRequest): Session {
        createdSessions += request
        return Session(id = "s-created", deepLink = "conductor://x", name = request.name)
    }

    override suspend fun session(sessionId: String) = Session(id = sessionId, deepLink = "conductor://workspace?id=w1&session=$sessionId", name = "Fix CI")

    override suspend fun sessionStatus(sessionId: String): SessionStatus =
        callOrder.add("status").let { _ -> SessionStatus("w1", sessionId, sessionStatuses[sessionId] ?: sessionStatusValue, "2026-10-05T06:00:00Z") }

    override suspend fun messagesAfter(sessionId: String, afterMessageId: String?): Page<Message> {
        messageCursors += afterMessageId
        callOrder += "messages"
        messagesFailure?.let { throw it }
        val start = afterMessageId?.let { id -> transcript.indexOfFirst { it.id == id } + 1 } ?: 0
        val page = transcript.drop(start).take(PAGE_SIZE)
        return Page(page, start, hasMore = start + page.size < transcript.size)
    }

    override suspend fun sendMessage(sessionId: String, text: String, messageId: String): SentMessage {
        sendFailure?.let { throw it }
        sentMessages += text to messageId
        return SentMessage(messageId, "queued", "conductor://x")
    }

    override suspend fun cancelSession(sessionId: String): CancelledSession {
        cancelCount++
        return CancelledSession("w1", sessionId, "working", canceledQueuedMessages)
    }

    override suspend fun favoriteModels(): List<FavoriteModel> = favorites

    companion object {
        const val SESSION_ID = "s1"
        const val VALID_KEY = "valid-key"
        const val PAGE_SIZE = 2

        fun workspace(id: String, name: String, lastActivityAt: String = "2026-10-05T06:00:00Z") = Workspace(
            id = id,
            name = name,
            state = "ready",
            repoUrl = "https://github.com/example-org/example-repo",
            createdAt = "2026-10-01T06:00:00Z",
            deepLink = "conductor://workspace?id=$id",
            lastActivityAt = lastActivityAt,
        )
    }
}
