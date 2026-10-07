package build.conductor.android.client.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.transcript.TranscriptItem
import build.conductor.android.client.ui.components.SessionStatusBadge
import build.conductor.android.client.ui.components.EmptyView
import build.conductor.android.client.ui.components.ErrorView
import build.conductor.android.client.ui.components.LoadingView
import build.conductor.android.client.ui.components.copyDeepLink
import build.conductor.android.client.ui.components.shareDeepLink
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(viewModel: SessionViewModel, onBack: () -> Unit, onStarRequested: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event -> if (event is SessionEvent.ShowMessage) snackbarHostState.showSnackbar(event.text) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { SessionActions(state, viewModel, onStarRequested) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        SessionBody(state, viewModel, padding)
    }
}

@Composable
private fun SessionActions(state: SessionUiState, viewModel: SessionViewModel, onStarRequested: () -> Unit) {
    val context = LocalContext.current
    var isMenuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { if (!state.isStarred) onStarRequested(); viewModel.toggleStar() }) {
        Icon(
            if (state.isStarred) Icons.Filled.Star else Icons.Outlined.Star,
            contentDescription = if (state.isStarred) "Stop notifications" else "Notify me when the agent finishes",
        )
    }
    IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
    val deepLink = state.deepLink ?: return
    IconButton(onClick = { isMenuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More actions") }
    DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
        DropdownMenuItem(text = { Text("Copy Conductor link") }, onClick = { isMenuOpen = false; copyDeepLink(context, deepLink) })
        DropdownMenuItem(text = { Text("Open in Conductor (share)") }, onClick = { isMenuOpen = false; shareDeepLink(context, state.title, deepLink) })
    }
}

@Composable
private fun SessionBody(state: SessionUiState, viewModel: SessionViewModel, padding: PaddingValues) {
    val transcriptScroll = rememberTranscriptScrollState()
    Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
        StatusBar(state, onCancel = viewModel::cancel)
        state.connectionProblem?.let { ConnectionBanner(it) }
        Column(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading && state.items.isEmpty() -> LoadingView()
                state.loadError != null -> ErrorView(state.loadError, viewModel::load)
                state.items.isEmpty() -> EmptyView("No messages yet", "Send a prompt to start the agent.")
                else -> Transcript(state.items, transcriptScroll) { item ->
                    SessionTranscriptRow(item, state, questionForm(state, viewModel, onSend = { transcriptScroll.followNewest(); viewModel.sendAnswers() }))
                }
            }
        }
        HorizontalDivider()
        Composer(state, onDraftChange = viewModel::onDraftChange, onSend = { transcriptScroll.followNewest(); viewModel.send() })
    }
}

@Composable
private fun StatusBar(state: SessionUiState, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SessionStatusBadge(state.status, hasOpenQuestion = state.openQuestion != null)
        if (state.status == AgentStatus.WORKING && state.openQuestion == null) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text = statusDescription(state),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (state.status == AgentStatus.WORKING) {
            OutlinedButton(onClick = onCancel, enabled = !state.isCancelling) { Text(if (state.isCancelling) "Stopping…" else "Stop") }
        }
    }
}

private fun statusDescription(state: SessionUiState): String = when {
    state.openQuestion != null -> "The agent waits for your answer below."
    else -> agentStatusDescription(state)
}

private fun agentStatusDescription(state: SessionUiState): String = when (state.status) {
    AgentStatus.WORKING -> "The agent is working."
    AgentStatus.IDLE -> "The agent is waiting for you."
    AgentStatus.ERROR -> state.statusError ?: "The agent stopped with an error."
    AgentStatus.UNKNOWN -> ""
}

@Composable
private fun ConnectionBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    }
}

@Composable
internal fun Transcript(
    items: List<TranscriptItem>,
    scrollState: TranscriptScrollState,
    row: @Composable (TranscriptItem) -> Unit = { TranscriptRow(it) },
) {
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(scrollState) { scrollState.trackUserPosition() }
    LaunchedEffect(items.lastOrNull()?.key) { scrollState.keepNewestInView() }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = scrollState.listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
        ) {
            items(items.asReversed(), key = { it.key }) { row(it) }
        }
        JumpToNewestButton(
            isVisible = !scrollState.isFollowingNewest,
            onClick = { coroutineScope.launch { scrollState.scrollToNewest() } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun JumpToNewestButton(isVisible: Boolean, onClick: () -> Unit, modifier: Modifier) {
    AnimatedVisibility(visible = isVisible, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = modifier) {
        SmallFloatingActionButton(onClick = onClick) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to the newest message")
        }
    }
}

/** The open question shows as a form; every other row draws as usual. */
@Composable
private fun SessionTranscriptRow(item: TranscriptItem, state: SessionUiState, form: QuestionForm) {
    if (item is TranscriptItem.Questions && item.toolUseId == state.openQuestion?.toolUseId) {
        OpenQuestionsCard(item, form)
    } else {
        TranscriptRow(item)
    }
}

private fun questionForm(state: SessionUiState, viewModel: SessionViewModel, onSend: () -> Unit) = QuestionForm(
    answers = state.questionAnswers,
    isSending = state.isSending,
    onToggle = viewModel::toggleOption,
    onOtherTextChange = viewModel::onOtherTextChange,
    onSend = onSend,
)

@Composable
private fun Composer(state: SessionUiState, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.draft,
            onValueChange = onDraftChange,
            placeholder = { Text(composerPlaceholder(state)) },
            maxLines = COMPOSER_MAX_LINES,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = onSend, enabled = state.draft.isNotBlank() && !state.isSending) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

private fun composerPlaceholder(state: SessionUiState): String = when {
    state.openQuestion != null -> "Or answer in your own words…"
    state.status == AgentStatus.WORKING -> "Steer the agent…"
    else -> "Message the agent…"
}

private const val COMPOSER_MAX_LINES = 6
