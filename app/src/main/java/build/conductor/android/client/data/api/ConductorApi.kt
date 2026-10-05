package build.conductor.android.client.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ConductorApi {
    @GET("me")
    suspend fun me(@Header(AUTHORIZATION_HEADER) authorization: String? = null): Me

    @GET("v0/projects")
    suspend fun projects(
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("offset") offset: Int = 0,
    ): Page<Project>

    @GET("v0/workspaces")
    suspend fun workspaces(
        @Query("repo") repo: String? = null,
        @Query("includeArchived") includeArchived: Boolean? = null,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("offset") offset: Int = 0,
    ): Page<Workspace>

    @GET("v0/workspaces/{workspaceId}")
    suspend fun workspace(@Path("workspaceId") workspaceId: String): Workspace

    @POST("v0/workspaces")
    suspend fun createWorkspace(@Body request: CreateWorkspaceRequest): CreatedWorkspace

    @POST("v0/workspaces/{workspaceId}/rename")
    suspend fun renameWorkspace(@Path("workspaceId") workspaceId: String, @Body request: RenameRequest): Workspace

    @POST("v0/workspaces/{workspaceId}/archive")
    suspend fun archiveWorkspace(@Path("workspaceId") workspaceId: String): ArchivedWorkspace

    @GET("v0/workspaces/{workspaceId}/status")
    suspend fun workspaceStatus(@Path("workspaceId") workspaceId: String): WorkspaceStatus

    @GET("v0/workspaces/{workspaceId}/sessions")
    suspend fun sessions(
        @Path("workspaceId") workspaceId: String,
        @Query("includeArchived") includeArchived: Boolean? = null,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("offset") offset: Int = 0,
    ): Page<Session>

    @POST("v0/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): Session

    @GET("v0/sessions/{sessionId}")
    suspend fun session(@Path("sessionId") sessionId: String): Session

    @GET("v0/sessions/{sessionId}/status")
    suspend fun sessionStatus(@Path("sessionId") sessionId: String): SessionStatus

    @GET("v0/sessions/{sessionId}/messages")
    suspend fun messages(
        @Path("sessionId") sessionId: String,
        @Query("after") after: String? = null,
        @Query("limit") limit: Int = PAGE_SIZE,
    ): Page<Message>

    @POST("v0/sessions/{sessionId}/messages")
    suspend fun sendMessage(@Path("sessionId") sessionId: String, @Body request: SendMessageRequest): SentMessage

    @POST("v0/sessions/{sessionId}/cancel")
    suspend fun cancelSession(@Path("sessionId") sessionId: String): CancelledSession

    @GET("v0/favorite-models")
    suspend fun favoriteModels(): FavoriteModels

    companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val PAGE_SIZE = 100
    }
}
