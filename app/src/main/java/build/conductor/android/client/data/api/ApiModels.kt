package build.conductor.android.client.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Page<T>(
    val data: List<T>,
    val offset: Int = 0,
    val hasMore: Boolean = false,
)

@Serializable
data class Me(
    val userId: String,
    val name: String? = null,
    val email: String? = null,
    val organizationId: String? = null,
    val authMethod: String? = null,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val gitRemote: String,
)

@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val state: String,
    val repoUrl: String,
    val createdAt: String,
    val deepLink: String,
    val projectId: String? = null,
    val lifecycleStep: String? = null,
    val creatorId: String? = null,
    val creatorName: String? = null,
    val lastActivityAt: String? = null,
)

@Serializable
data class WorkspaceStatus(
    val workspaceId: String,
    val status: String,
    val updatedAt: String,
    val lifecycleStep: String? = null,
    val errorMessage: String? = null,
)

@Serializable
data class Session(
    val id: String,
    val deepLink: String,
    val name: String? = null,
    val model: String? = null,
    val resolvedModel: String? = null,
    val effort: String? = null,
    val fastMode: Boolean? = null,
    val archivedAt: String? = null,
    val initialMessage: SentMessage? = null,
)

@Serializable
data class SessionStatus(
    val workspaceId: String,
    val sessionId: String,
    val status: String,
    val updatedAt: String,
    val errorMessage: String? = null,
    val lastError: String? = null,
    val lastErrorAt: String? = null,
)

@Serializable
data class Message(
    val id: String,
    val sessionId: String,
    val sessionIndex: Long,
    val type: String,
    val content: JsonElement,
    val receivedAt: String,
)

@Serializable
data class SentMessage(
    val messageId: String,
    val state: String,
    val deepLink: String,
)

@Serializable
data class SendMessageRequest(
    val message: String,
    val messageId: String,
)

@Serializable
data class CreateWorkspaceRequest(
    val projectId: String,
    val name: String? = null,
    val branch: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val effort: String? = null,
    val message: String? = null,
)

@Serializable
data class CreatedWorkspace(
    val workspaceId: String,
    val sessionId: String,
    val deepLink: String,
    val initialMessage: SentMessage? = null,
)

@Serializable
data class CreateSessionRequest(
    val workspaceId: String,
    val agent: String,
    val model: String? = null,
    val effort: String? = null,
    val name: String? = null,
    val message: String? = null,
    val messageId: String? = null,
)

@Serializable
data class RenameRequest(val name: String)

@Serializable
data class ArchivedWorkspace(
    val workspaceId: String,
    val status: String,
)

@Serializable
data class CancelledSession(
    val workspaceId: String,
    val sessionId: String,
    val status: String,
    val canceledQueuedMessages: Int = 0,
)

@Serializable
data class FavoriteModels(val favoriteModels: List<FavoriteModel>)

@Serializable
data class FavoriteModel(
    val agent: String,
    val model: String,
    val effort: String? = null,
    val fastMode: Boolean? = null,
)

@Serializable
data class StructuredError(
    val userMessage: String,
    val code: String? = null,
    val retryable: Boolean? = null,
    val traceId: String? = null,
)
