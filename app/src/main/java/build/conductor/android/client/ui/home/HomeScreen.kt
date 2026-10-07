package build.conductor.android.client.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.ui.components.EmptyView
import build.conductor.android.client.ui.components.ErrorView
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.LoadingView
import build.conductor.android.client.ui.workspaces.WorkspaceRow

/** The screens that the home screen opens. */
class HomeActions(
    val onOpenWorkspace: (Workspace) -> Unit,
    val onCreateWorkspace: () -> Unit,
    val onOpenProjects: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, actions: HomeActions) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.onSnackbarShown() }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Workspaces") }, actions = { HomeTopActions(actions) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onCreateWorkspace,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New workspace") },
            )
        },
    ) { padding ->
        HomeBody(state, viewModel, actions.onOpenWorkspace, Modifier.padding(padding))
    }
}

@Composable
private fun HomeTopActions(actions: HomeActions) {
    IconButton(onClick = actions.onOpenProjects) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Projects") }
    IconButton(onClick = actions.onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeBody(state: HomeUiState, viewModel: HomeViewModel, onOpenWorkspace: (Workspace) -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        FilterChip(
            selected = state.isShowingArchived,
            onClick = { viewModel.setShowingArchived(!state.isShowingArchived) },
            label = { Text("Show archived") },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            when (val groups = state.groups) {
                LoadState.Loading -> LoadingView()
                is LoadState.Failed -> ErrorView(groups.message, viewModel::load)
                is LoadState.Loaded -> WorkspaceGroupList(groups.value, onOpenWorkspace)
            }
        }
    }
}

@Composable
internal fun WorkspaceGroupList(groups: List<WorkspaceGroup>, onOpenWorkspace: (Workspace) -> Unit) {
    if (groups.isEmpty()) {
        EmptyView("No workspaces", "Tap New workspace to start one in the cloud.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group -> workspaceGroup(group, onOpenWorkspace) }
    }
}

private fun LazyListScope.workspaceGroup(group: WorkspaceGroup, onOpenWorkspace: (Workspace) -> Unit) {
    group.title?.let { title -> stickyHeader(key = "header/${group.key}") { SectionHeader(title) } }
    // A workspace can be in more than one section, so the key includes the group.
    items(group.workspaces, key = { "${group.key}/${it.id}" }) { workspace ->
        WorkspaceRow(workspace, onClick = { onOpenWorkspace(workspace) })
        HorizontalDivider()
    }
}

@Composable
private fun SectionHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
