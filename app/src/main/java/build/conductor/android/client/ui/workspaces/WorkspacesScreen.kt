package build.conductor.android.client.ui.workspaces

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.R
import build.conductor.android.client.data.WorkspaceState
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.ui.components.EmptyView
import build.conductor.android.client.ui.components.ErrorView
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.LoadingView
import build.conductor.android.client.ui.components.WorkspaceStateBadge
import build.conductor.android.client.ui.components.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacesScreen(
    viewModel: WorkspacesViewModel,
    projectName: String,
    onOpenWorkspace: (Workspace) -> Unit,
    onCreateWorkspace: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.onSnackbarShown() }
    }
    UndoArchiveSnackbar(state.undoableArchive, snackbarHostState, viewModel)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(projectName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateWorkspace,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New workspace") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterChip(
                selected = state.isShowingArchived,
                onClick = { viewModel.setShowingArchived(!state.isShowingArchived) },
                label = { Text("Show archived") },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                when (val workspaces = state.workspaces) {
                    LoadState.Loading -> LoadingView()
                    is LoadState.Failed -> ErrorView(workspaces.message, viewModel::load)
                    is LoadState.Loaded -> WorkspaceList(workspaces.value, state, WorkspaceActions(onOpenWorkspace, viewModel::archive, viewModel::loadMore))
                }
            }
        }
    }
}

/** Shows "Undo" while the undo window is open. The snackbar closes when the view model closes the window. */
@Composable
private fun UndoArchiveSnackbar(workspace: Workspace?, snackbarHostState: SnackbarHostState, viewModel: WorkspacesViewModel) {
    LaunchedEffect(workspace?.id) {
        if (workspace == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar("Archived \"${workspace.name}\"", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoArchive()
            SnackbarResult.Dismissed -> viewModel.confirmArchive()
        }
    }
}

private class WorkspaceActions(
    val onOpen: (Workspace) -> Unit,
    val onArchive: (Workspace) -> Unit,
    val onLoadMore: () -> Unit,
)

@Composable
private fun WorkspaceList(workspaces: List<Workspace>, state: WorkspacesUiState, actions: WorkspaceActions) {
    val visibleWorkspaces = workspaces.filterNot { it.id in state.archivingIds }
    if (visibleWorkspaces.isEmpty()) {
        EmptyView("No workspaces", "Tap New workspace to start one in the cloud.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(visibleWorkspaces, key = { it.id }) { workspace ->
            Column(modifier = Modifier.animateItem()) {
                SwipeToArchiveRow(workspace, onClick = { actions.onOpen(workspace) }, onArchive = { actions.onArchive(workspace) })
                HorizontalDivider()
            }
        }
        if (state.hasMore || state.loadMoreError != null) {
            item(key = "load-more") { LoadMoreRow(state, actions.onLoadMore) }
        }
    }
}

/** A swipe towards the start archives the workspace. Screen readers get the same action from the actions menu. */
@Composable
private fun SwipeToArchiveRow(workspace: Workspace, onClick: () -> Unit, onArchive: () -> Unit) {
    val isArchivable = WorkspaceState.from(workspace.state) != WorkspaceState.ARCHIVED
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        backgroundContent = { ArchiveSwipeBackground() },
        modifier = Modifier.semantics {
            if (isArchivable) customActions = listOf(CustomAccessibilityAction("Archive") { onArchive(); true })
        },
        enableDismissFromStartToEnd = false,
        gesturesEnabled = isArchivable,
        onDismiss = { onArchive() },
    ) {
        WorkspaceRow(workspace, onClick)
    }
}

@Composable
private fun ArchiveSwipeBackground() {
    Row(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Archive", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_archive), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
internal fun WorkspaceRow(workspace: Workspace, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(workspace.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(repositoryName(workspace.repoUrl), relativeTime(workspace.lastActivityAt ?: workspace.createdAt)).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { WorkspaceStateBadge(WorkspaceState.from(workspace.state), workspace.lifecycleStep) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun LoadMoreRow(state: WorkspacesUiState, onLoadMore: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        if (state.isLoadingMore) {
            CircularProgressIndicator()
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                state.loadMoreError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = onLoadMore) { Text(if (state.loadMoreError != null) "Try again" else "Load more") }
            }
        }
    }
}

/** "owner/repo" from a repository URL. */
fun repositoryName(repoUrl: String): String = repoUrl.trimEnd('/').removeSuffix(".git").split('/').takeLast(2).joinToString("/")
