package build.conductor.android.client.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data class SettingsRoute(val notice: String? = null)

@Serializable
data object ProjectsRoute

@Serializable
data class WorkspacesRoute(val projectId: String, val projectName: String)

@Serializable
data class CreateWorkspaceRoute(val projectId: String, val projectName: String)

@Serializable
data class WorkspaceRoute(val workspaceId: String)

@Serializable
data class SessionRoute(val sessionId: String, val title: String)
