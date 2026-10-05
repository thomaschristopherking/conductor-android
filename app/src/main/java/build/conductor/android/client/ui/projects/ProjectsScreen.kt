package build.conductor.android.client.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.data.api.Project
import build.conductor.android.client.ui.components.EmptyView
import build.conductor.android.client.ui.components.ErrorView
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (Project) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val projects = state.projects) {
                LoadState.Loading -> LoadingView()
                is LoadState.Failed -> ErrorView(projects.message, viewModel::load)
                is LoadState.Loaded -> ProjectList(projects.value, onOpenProject)
            }
        }
    }
}

@Composable
private fun ProjectList(projects: List<Project>, onOpenProject: (Project) -> Unit) {
    if (projects.isEmpty()) {
        EmptyView("No projects", "Add a repository in the Conductor app. It then appears here.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(projects, key = { it.id }) { project ->
            ListItem(
                headlineContent = { Text(project.name) },
                supportingContent = { Text(project.gitRemote.removePrefix("https://")) },
                modifier = Modifier.clickable { onOpenProject(project) },
            )
            HorizontalDivider()
        }
    }
}
