package build.conductor.android.client.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.data.WorkspaceState
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.ui.components.EmptyView
import build.conductor.android.client.ui.components.ErrorView
import build.conductor.android.client.ui.components.LoadState
import build.conductor.android.client.ui.components.LoadingView
import build.conductor.android.client.ui.components.copyDeepLink
import build.conductor.android.client.ui.components.shareDeepLink
import build.conductor.android.client.ui.components.workspaceStateLabel
import build.conductor.android.client.ui.session.SessionPane
import build.conductor.android.client.ui.session.SessionViewModel
import build.conductor.android.client.ui.workspaces.repositoryName

private enum class WorkspaceDialog { RENAME, ARCHIVE, NEW_SESSION }

/** What the workspace screen asks of the app outside it. */
class WorkspaceScreenActions(
    val sessionViewModel: @Composable (SessionRow) -> SessionViewModel,
    val onSubscribeRequested: () -> Unit,
    val onArchived: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
fun WorkspaceScreen(viewModel: WorkspaceDetailViewModel, actions: WorkspaceScreenActions) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var openDialog by remember { mutableStateOf<WorkspaceDialog?>(null) }
    WorkspaceEffects(viewModel, state, snackbarHostState, actions.onArchived)
    val detail = (state.detail as? LoadState.Loaded)?.value
    val selectedRow = detail?.sessions?.firstOrNull { it.session.id == state.selectedSessionId }
    val selectedSession = selectedRow?.let { actions.sessionViewModel(it) }
    Scaffold(
        topBar = {
            WorkspaceTopBar(viewModel, selectedSession, actions) { dialog -> openDialog = dialog }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        WorkspaceBody(state, viewModel, padding) { sessions ->
            SessionTabs(sessions, state.selectedSessionId, viewModel, TabCallbacks(actions.sessionViewModel, snackbarHostState) { openDialog = WorkspaceDialog.NEW_SESSION })
        }
    }
    WorkspaceDialogs(openDialog, detail?.workspace, state, viewModel, onDismiss = { openDialog = null })
}

@Composable
private fun WorkspaceEffects(viewModel: WorkspaceDetailViewModel, state: WorkspaceDetailUiState, snackbarHostState: SnackbarHostState, onArchived: () -> Unit) {
    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event -> if (event == WorkspaceEvent.Archived) onArchived() }
    }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.onSnackbarShown() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceTopBar(viewModel: WorkspaceDetailViewModel, selectedSession: SessionViewModel?, actions: WorkspaceScreenActions, onDialog: (WorkspaceDialog) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val workspace = (state.detail as? LoadState.Loaded)?.value?.workspace
    TopAppBar(
        title = { WorkspaceTitle(workspace) },
        navigationIcon = {
            IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = {
            if (workspace != null) SubscribeButton(state.isSubscribed) { if (!state.isSubscribed) actions.onSubscribeRequested(); viewModel.toggleSubscription() }
            if (selectedSession != null) IconButton(onClick = selectedSession::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
            if (workspace != null) WorkspaceMenu(workspace, selectedSession, onDialog)
        },
    )
}

@Composable
private fun WorkspaceTitle(workspace: Workspace?) {
    Column {
        Text(workspace?.name ?: "Workspace", maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (workspace != null) {
            Text(workspaceSubtitle(workspace), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun workspaceSubtitle(workspace: Workspace): String {
    val state = WorkspaceState.from(workspace.state)
    val repository = repositoryName(workspace.repoUrl)
    return if (state == WorkspaceState.READY) repository else "$repository · ${workspaceStateLabel(state, workspace.lifecycleStep)}"
}

@Composable
private fun SubscribeButton(isSubscribed: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (isSubscribed) Icons.Filled.Notifications else Icons.Outlined.Notifications,
            contentDescription = if (isSubscribed) "Stop notifications for this workspace" else "Notify me when an agent in this workspace finishes",
        )
    }
}

/** The Conductor link of the open session opens the workspace at that session, so it replaces the workspace link. */
@Composable
private fun WorkspaceMenu(workspace: Workspace, selectedSession: SessionViewModel?, onDialog: (WorkspaceDialog) -> Unit) {
    val context = LocalContext.current
    val sessionLink = selectedSession?.uiState?.collectAsStateWithLifecycle()?.value?.deepLink
    val deepLink = sessionLink ?: workspace.deepLink
    var isExpanded by remember { mutableStateOf(false) }
    IconButton(onClick = { isExpanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More actions") }
    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
        DropdownMenuItem(text = { Text("Copy Conductor link") }, onClick = { isExpanded = false; copyDeepLink(context, deepLink) })
        DropdownMenuItem(text = { Text("Open in Conductor (share)") }, onClick = { isExpanded = false; shareDeepLink(context, workspace.name, deepLink) })
        DropdownMenuItem(text = { Text("Rename workspace") }, onClick = { isExpanded = false; onDialog(WorkspaceDialog.RENAME) })
        if (WorkspaceState.from(workspace.state) != WorkspaceState.ARCHIVED) {
            DropdownMenuItem(text = { Text("Archive workspace") }, onClick = { isExpanded = false; onDialog(WorkspaceDialog.ARCHIVE) })
        }
    }
}

@Composable
private fun WorkspaceBody(
    state: WorkspaceDetailUiState,
    viewModel: WorkspaceDetailViewModel,
    padding: PaddingValues,
    tabs: @Composable (List<SessionRow>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
        if (state.isBusy || state.isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        when (val detail = state.detail) {
            LoadState.Loading -> LoadingView()
            is LoadState.Failed -> ErrorView(detail.message, viewModel::load)
            is LoadState.Loaded -> tabs(detail.value.sessions)
        }
    }
}

/** What the tabs need from the screen. */
private class TabCallbacks(
    val sessionViewModel: @Composable (SessionRow) -> SessionViewModel,
    val snackbarHostState: SnackbarHostState,
    val onNewSession: () -> Unit,
)

@Composable
private fun SessionTabs(sessions: List<SessionRow>, selectedSessionId: String?, viewModel: WorkspaceDetailViewModel, callbacks: TabCallbacks) {
    val selectedIndex = sessions.indexOfFirst { it.session.id == selectedSessionId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { sessions.size }
    SyncPagerWithSelection(pagerState, sessions, selectedIndex, viewModel::selectSession)
    Column(modifier = Modifier.fillMaxSize()) {
        SessionTabRow(sessions, selectedIndex.takeIf { sessions.isNotEmpty() }, onSelect = viewModel::selectSession, onNewSession = callbacks.onNewSession)
        if (sessions.isEmpty()) {
            EmptyView("No sessions", "Tap + to start an agent chat.")
            return@Column
        }
        HorizontalPager(state = pagerState, key = { sessions[it].session.id }, modifier = Modifier.weight(1f)) { page ->
            val row = sessions[page]
            Box(modifier = Modifier.fillMaxSize()) {
                SessionPane(callbacks.sessionViewModel(row), callbacks.snackbarHostState) { status, hasOpenQuestion ->
                    viewModel.recordSessionStatus(row.session.id, status, hasOpenQuestion)
                }
            }
        }
    }
}

/** A tap on a tab moves the pager; a swipe of the pager selects the tab. */
@Composable
private fun SyncPagerWithSelection(pagerState: PagerState, sessions: List<SessionRow>, selectedIndex: Int, onSelect: (String) -> Unit) {
    val currentSessions by rememberUpdatedState(sessions)
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < pagerState.pageCount && pagerState.currentPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> currentSessions.getOrNull(page)?.let { onSelect(it.session.id) } }
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
