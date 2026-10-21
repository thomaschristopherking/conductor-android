package build.conductor.android.client.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.data.WorkspaceState
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.ui.components.SessionStatusBadge
import build.conductor.android.client.ui.components.EmptyView
import build.conductor.android.client.ui.components.ErrorView
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.LoadingView
import build.conductor.android.client.ui.components.WorkspaceStateBadge
import build.conductor.android.client.ui.components.copyDeepLink
import build.conductor.android.client.ui.components.relativeTime
import build.conductor.android.client.ui.components.shareDeepLink
import build.conductor.android.client.ui.workspaces.repositoryName

private enum class WorkspaceDialog { RENAME, ARCHIVE, NEW_SESSION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceDetailScreen(
    viewModel: WorkspaceDetailViewModel,
    onOpenSession: (sessionId: String, title: String) -> Unit,
    onArchived: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var openDialog by remember { mutableStateOf<WorkspaceDialog?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is WorkspaceEvent.OpenSession -> onOpenSession(event.sessionId, event.title)
                WorkspaceEvent.Archived -> onArchived()
            }
        }
    }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.onSnackbarShown() }
    }
    val workspace = (state.detail as? LoadState.Loaded)?.value?.workspace
    Scaffold(
        topBar = { WorkspaceTopBar(workspace, onBack, onDialog = { openDialog = it }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (workspace != null) {
                ExtendedFloatingActionButton(
                    onClick = { openDialog = WorkspaceDialog.NEW_SESSION },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New session") },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                when (val detail = state.detail) {
                    LoadState.Loading -> LoadingView()
                    is LoadState.Failed -> ErrorView(detail.message, viewModel::load)
                    is LoadState.Loaded -> WorkspaceContent(detail.value, onOpenSession)
                }
            }
        }
    }
    WorkspaceDialogs(openDialog, workspace, state, viewModel, onDismiss = { openDialog = null })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceTopBar(workspace: Workspace?, onBack: () -> Unit, onDialog: (WorkspaceDialog) -> Unit) {
    TopAppBar(
        title = { Text(workspace?.name ?: "Workspace", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = { if (workspace != null) WorkspaceMenu(workspace, onDialog) },
    )
}

@Composable
private fun WorkspaceMenu(workspace: Workspace, onDialog: (WorkspaceDialog) -> Unit) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    IconButton(onClick = { isExpanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More actions") }
    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
        DropdownMenuItem(text = { Text("Rename") }, onClick = { isExpanded = false; onDialog(WorkspaceDialog.RENAME) })
        DropdownMenuItem(text = { Text("Copy Conductor link") }, onClick = { isExpanded = false; copyDeepLink(context, workspace.deepLink) })
        DropdownMenuItem(
            text = { Text("Open in Conductor (share)") },
            onClick = { isExpanded = false; shareDeepLink(context, workspace.name, workspace.deepLink) },
        )
        if (WorkspaceState.from(workspace.state) != WorkspaceState.ARCHIVED) {
            DropdownMenuItem(text = { Text("Archive") }, onClick = { isExpanded = false; onDialog(WorkspaceDialog.ARCHIVE) })
        }
    }
}

@Composable
private fun WorkspaceContent(detail: WorkspaceDetail, onOpenSession: (String, String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "header") { WorkspaceHeader(detail.workspace) }
        if (detail.sessions.isEmpty()) {
            item(key = "empty") { EmptyView("No sessions", "Tap New session to start an agent chat.") }
        }
        items(detail.sessions, key = { it.session.id }) { row ->
            val title = row.session.name ?: "Untitled session"
            ListItem(
                headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(listOfNotNull(row.session.model, row.session.effort).joinToString(" · ")) },
                trailingContent = { SessionStatusBadge(row.status, row.hasOpenQuestion) },
                modifier = Modifier.clickable { onOpenSession(row.session.id, title) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun WorkspaceHeader(workspace: Workspace) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkspaceStateBadge(WorkspaceState.from(workspace.state), workspace.lifecycleStep)
                relativeTime(workspace.lastActivityAt)?.let { Text("Active $it", style = MaterialTheme.typography.bodySmall) }
            }
            Text(repositoryName(workspace.repoUrl), style = MaterialTheme.typography.bodyMedium)
            workspace.creatorName?.let { Text("Created by $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun WorkspaceDialogs(
    openDialog: WorkspaceDialog?,
    workspace: Workspace?,
    state: WorkspaceDetailUiState,
    viewModel: WorkspaceDetailViewModel,
    onDismiss: () -> Unit,
) {
    if (workspace == null) return
    when (openDialog) {
        WorkspaceDialog.RENAME -> RenameDialog(workspace.name, onConfirm = { onDismiss(); viewModel.rename(it) }, onDismiss = onDismiss)
        WorkspaceDialog.ARCHIVE -> ArchiveDialog(workspace.name, onConfirm = { onDismiss(); viewModel.archive() }, onDismiss = onDismiss)
        WorkspaceDialog.NEW_SESSION -> NewSessionDialog(
            initialSelection = state.defaultSelection,
            favorites = state.favorites,
            onConfirm = { onDismiss(); viewModel.createSession(it) },
            onDismiss = onDismiss,
        )
        null -> Unit
    }
}
