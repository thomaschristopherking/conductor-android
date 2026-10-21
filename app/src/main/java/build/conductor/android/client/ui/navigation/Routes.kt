package build.conductor.android.client.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data class SettingsRoute(val notice: String? = null)

@Serializable
data object HomeRoute

@Serializable
data object ProjectsRoute

@Serializable
data object ChooseProjectRoute

@Serializable
data class WorkspacesRoute(val projectId: String, val projectName: String)

@Serializable
data class CreateWorkspaceRoute(val projectId: String, val projectName: String)

/** [sessionId] chooses the session tab that opens first. */
@Serializable
data class WorkspaceRoute(val workspaceId: String, val sessionId: String? = null)
