package build.conductor.android.client.ui.pullrequest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.data.github.PullRequestStack
import build.conductor.android.client.data.github.PullRequestState
import build.conductor.android.client.data.github.PullRequestSummary
import build.conductor.android.client.ui.components.StackPositionChip

/** A line under the title bar: the pull request of the workspace, a menu of the tiers of its stack, and its diff numbers. */
@Composable
fun PullRequestBar(viewModel: PullRequestViewModel, onConnectGitHub: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.refresh()
        onStopOrDispose {}
    }
    when (val lookup = state.lookup) {
        StackLookup.NotOnGitHub -> return
        StackLookup.NotConnected -> BarSurface { TextButton(onClick = onConnectGitHub) { Text("Connect GitHub to see the pull request and its changes") } }
        StackLookup.Loading -> BarSurface { LoadingLine() }
        StackLookup.NotFound -> BarSurface { MessageLine("No pull request for this workspace", onRefresh = viewModel::refresh) }
        is StackLookup.Failed -> BarSurface { FailedLine(lookup, onRetry = viewModel::refresh, onOpenSettings = onConnectGitHub) }
        is StackLookup.Found -> BarSurface { FoundLine(lookup.stack, state, viewModel) }
    }
    if (state.isShowingChanges) ChangesSheet(state, viewModel)
}

@Composable
private fun BarSurface(content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun RowScope.LoadingLine() {
    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(16.dp), strokeWidth = 2.dp)
    Text("Finding the pull request…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp).weight(1f))
}

@Composable
private fun RowScope.MessageLine(message: String, onRefresh: () -> Unit) {
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).weight(1f))
    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Look for the pull request again") }
}

@Composable
private fun RowScope.FailedLine(failure: StackLookup.Failed, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    Text(failure.message, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f))
    if (failure.isTokenRejected) TextButton(onClick = onOpenSettings) { Text("Settings") } else TextButton(onClick = onRetry) { Text("Retry") }
}

@Composable
private fun RowScope.FoundLine(stack: PullRequestStack, state: PullRequestUiState, viewModel: PullRequestViewModel) {
    val tier = state.selectedTier ?: stack.workspaceTier
    var isMenuOpen by remember { mutableStateOf(false) }
    Box(modifier = Modifier.weight(1f)) {
        TierButton(tier, stack, onClick = { isMenuOpen = true })
        StackMenu(stack, tier.number, isMenuOpen, onDismiss = { isMenuOpen = false }, onSelect = { isMenuOpen = false; viewModel.selectTier(it) })
    }
    DiffButton(tier, onClick = viewModel::showChanges)
}

/** "#15 Title ▾", with "2 of 5" when the pull request is part of a stack. */
@Composable
private fun TierButton(tier: PullRequestSummary, stack: PullRequestStack, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics { contentDescription = "Pull request ${tier.number}, ${tier.title}. Choose a pull request of the stack." },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StateDot(tier.state)
        Text("#${tier.number}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(tier.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        stack.positionOf(tier.number)?.let { StackPositionChip(it) }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
}

/** The tiers from the top of the stack down to the branch that the stack merges into, as in the Conductor desktop app. */
@Composable
private fun StackMenu(stack: PullRequestStack, selectedNumber: Int, isOpen: Boolean, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    DropdownMenu(expanded = isOpen, onDismissRequest = onDismiss, modifier = Modifier.widthIn(max = 380.dp)) {
        StackMenuItems(stack, selectedNumber, onDismiss, onSelect)
    }
}

@Composable
internal fun StackMenuItems(stack: PullRequestStack, selectedNumber: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val uriHandler = LocalUriHandler.current
    Text(stackHeading(stack), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    stack.tiers.forEach { tier ->
        DropdownMenuItem(
            text = { TierMenuText(tier, isWorkspaceTier = tier.number == stack.workspaceNumber) },
            leadingIcon = { StateDot(tier.state) },
            trailingIcon = { if (tier.number == selectedNumber) Icon(Icons.Filled.Check, contentDescription = "Shown") },
            onClick = { onSelect(tier.number) },
        )
    }
    Text("↳ ${stack.baseRef}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    HorizontalDivider()
    DropdownMenuItem(text = { Text("Open #$selectedNumber on GitHub") }, onClick = { onDismiss(); stack.tiers.firstOrNull { it.number == selectedNumber }?.let { uriHandler.openUri(it.url) } })
}

private fun stackHeading(stack: PullRequestStack): String =
    if (stack.tiers.size > 1) "Stack of ${stack.tiers.size} pull requests" else "Pull request"

@Composable
private fun TierMenuText(tier: PullRequestSummary, isWorkspaceTier: Boolean) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("#${tier.number}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(tier.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stateLabel(tier.state), style = MaterialTheme.typography.labelSmall, color = diffColors().of(tier.state))
            DiffText(tier.diff, MaterialTheme.typography.labelSmall)
            if (isWorkspaceTier) Text("· This workspace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "+1,005 −415"; a tap opens the changed files. */
@Composable
private fun DiffButton(tier: PullRequestSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 4.dp).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics { contentDescription = "${tier.changedFiles} files changed, ${tier.diff.additions} lines added, ${tier.diff.deletions} removed. Show the changes." },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DiffText(tier.diff)
    }
}

@Composable
fun StateDot(state: PullRequestState) {
    Box(modifier = Modifier.size(10.dp).background(diffColors().of(state), CircleShape))
}

fun stateLabel(state: PullRequestState): String = when (state) {
    PullRequestState.OPEN -> "Open"
    PullRequestState.DRAFT -> "Draft"
    PullRequestState.MERGED -> "Merged"
    PullRequestState.CLOSED -> "Closed"
}
