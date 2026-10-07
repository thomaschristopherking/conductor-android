package build.conductor.android.client.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import build.conductor.android.client.AppContainer
import build.conductor.android.client.ui.projects.ProjectsScreen
import build.conductor.android.client.ui.projects.ProjectsViewModel
import build.conductor.android.client.ui.session.SessionScreen
import build.conductor.android.client.ui.session.SessionViewModel
import build.conductor.android.client.ui.settings.AppearanceViewModel
import build.conductor.android.client.ui.settings.SettingsScreen
import build.conductor.android.client.ui.settings.SettingsViewModel
import build.conductor.android.client.ui.workspace.WorkspaceDetailScreen
import build.conductor.android.client.ui.workspace.WorkspaceDetailViewModel
import build.conductor.android.client.ui.workspaces.CreateWorkspaceScreen
import build.conductor.android.client.ui.workspaces.CreateWorkspaceViewModel
import build.conductor.android.client.ui.workspaces.WorkspacesScreen
import build.conductor.android.client.ui.workspaces.WorkspacesViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

private const val FIRST_RUN_NOTICE = "Welcome. Paste a Conductor API key to connect the app to your cloud workspaces."
private const val REJECTED_KEY_NOTICE = "Conductor rejected the saved API key. Paste a new key to continue."

@Composable
fun AppNavHost(container: AppContainer, hasApiKey: Boolean, requestedSession: MutableStateFlow<SessionRoute?>) {
    val navController = rememberNavController()
    LaunchedEffect(container) {
        container.repository.unauthorizedEvents.collect { navController.openSettingsAsRoot(REJECTED_KEY_NOTICE) }
    }
    LaunchedEffect(requestedSession, hasApiKey) {
        if (!hasApiKey) return@LaunchedEffect
        requestedSession.filterNotNull().collect { route ->
            navController.navigate(route)
            requestedSession.value = null
        }
    }
    val start: Any = if (hasApiKey) ProjectsRoute else SettingsRoute(FIRST_RUN_NOTICE)
    NavHost(navController = navController, startDestination = start) {
        composable<SettingsRoute> { entry ->
            val hasPrevious = navController.previousBackStackEntry != null
            SettingsScreen(
                viewModel = viewModel { SettingsViewModel(container.repository, container.apiKeyStore, container.apiKeyState) },
                appearanceViewModel = viewModel { AppearanceViewModel(container.appearanceStore) },
                notice = entry.toRoute<SettingsRoute>().notice,
                onKeySaved = { if (hasPrevious) navController.popBackStack() else navController.openProjectsAsRoot() },
                onBack = if (hasPrevious) ({ navController.popBackStack() }) else null,
            )
        }
        composable<ProjectsRoute> {
            ProjectsScreen(
                viewModel = viewModel { ProjectsViewModel(container.repository) },
                onOpenProject = { navController.navigate(WorkspacesRoute(it.id, it.name)) },
                onOpenSettings = { navController.navigate(SettingsRoute()) },
            )
        }
        composable<WorkspacesRoute> { entry ->
            val route = entry.toRoute<WorkspacesRoute>()
            WorkspacesScreen(
                viewModel = viewModel { WorkspacesViewModel(container.repository, route.projectId, container.applicationScope) },
                projectName = route.projectName,
                onOpenWorkspace = { navController.navigate(WorkspaceRoute(it.id)) },
                onCreateWorkspace = { navController.navigate(CreateWorkspaceRoute(route.projectId, route.projectName)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<CreateWorkspaceRoute> { entry ->
            val route = entry.toRoute<CreateWorkspaceRoute>()
            CreateWorkspaceScreen(
                viewModel = viewModel { CreateWorkspaceViewModel(container.repository, route.projectId) },
                projectName = route.projectName,
                onCreated = { created ->
                    navController.popBackStack()
                    navController.navigate(WorkspaceRoute(created.workspaceId))
                    navController.navigate(SessionRoute(created.sessionId, "New session"))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<WorkspaceRoute> { entry ->
            val route = entry.toRoute<WorkspaceRoute>()
            WorkspaceDetailScreen(
                viewModel = viewModel { WorkspaceDetailViewModel(container.repository, route.workspaceId, container.openQuestionTracker) },
                onOpenSession = { sessionId, title -> navController.navigate(SessionRoute(sessionId, title)) },
                onArchived = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable<SessionRoute> { entry ->
            val route = entry.toRoute<SessionRoute>()
            val requestNotifications = rememberNotificationPermissionRequest()
            SessionScreen(
                viewModel = viewModel { SessionViewModel(container.repository, route.sessionId, route.title, container.starredSessionStore) },
                onBack = { navController.popBackStack() },
                onStarRequested = requestNotifications,
            )
        }
    }
}

private fun NavHostController.openSettingsAsRoot(notice: String) {
    navigate(SettingsRoute(notice)) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.openProjectsAsRoot() {
    navigate(ProjectsRoute) { popUpTo(graph.id) { inclusive = true } }
}

/** Returns an action that asks for the notification permission on Android 13 and later, if the app does not have it. */
@Composable
private fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        val isGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!isGranted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
