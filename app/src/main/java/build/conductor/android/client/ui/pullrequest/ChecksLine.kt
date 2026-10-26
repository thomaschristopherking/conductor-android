package build.conductor.android.client.ui.pullrequest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import build.conductor.android.client.data.github.Check
import build.conductor.android.client.data.github.CheckOutcome
import build.conductor.android.client.data.github.Mergeability
import build.conductor.android.client.data.github.PullRequestStack
import build.conductor.android.client.data.github.PullRequestSummary
import build.conductor.android.client.ui.components.LoadState

/** The second line of the bar for an open or draft tier: its checks, Fix errors when a check failed, and Merge for the bottom tier. */
@Composable
internal fun ChecksLine(stack: PullRequestStack, tier: PullRequestSummary, state: PullRequestUiState, viewModel: PullRequestViewModel, canSendPrompt: Boolean) {
    val checks = state.checks[tier.number] ?: LoadState.Loading
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChecksSummary(checks)
        val failedChecks = (checks as? LoadState.Loaded)?.value.orEmpty().count { it.outcome == CheckOutcome.FAILED }
        if (canSendPrompt && failedChecks > 0) FixErrorsButton(state.isPreparingFix, viewModel::fixErrors)
        if (stack.canMerge(tier)) MergeControl(tier.mergeability, viewModel::requestMerge)
    }
}

/** "2 of 7 checks failed ▾"; a tap lists the checks, and a tap on a check opens it in the browser. */
@Composable
private fun RowScope.ChecksSummary(checks: LoadState<List<Check>>) {
    var isMenuOpen by remember { mutableStateOf(false) }
    Box(modifier = Modifier.weight(1f)) {
        Row(
            modifier = Modifier.clickable(enabled = checks !is LoadState.Loading) { isMenuOpen = true }.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChecksSummaryContent(checks)
        }
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }, modifier = Modifier.widthIn(max = 380.dp)) {
            ChecksMenuItems(checks, onDismiss = { isMenuOpen = false })
        }
    }
}

@Composable
private fun RowScope.ChecksSummaryContent(checks: LoadState<List<Check>>) {
    when (checks) {
        LoadState.Loading -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
        is LoadState.Failed -> OutcomeDot(CheckOutcome.FAILED)
        is LoadState.Loaded -> OutcomeDot(overallOutcome(checks.value))
    }
    val label = when (checks) {
        LoadState.Loading -> "Reading checks…"
        is LoadState.Failed -> "Checks not available"
        is LoadState.Loaded -> checksLabel(checks.value)
    }
    Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    if (checks !is LoadState.Loading) Icon(Icons.Filled.ArrowDropDown, contentDescription = "Show the checks", modifier = Modifier.size(18.dp))
}

@Composable
private fun ChecksMenuItems(checks: LoadState<List<Check>>, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    when (checks) {
        LoadState.Loading -> Unit
        is LoadState.Failed -> MenuNote(checks.message)
        is LoadState.Loaded -> {
            if (checks.value.isEmpty()) MenuNote("GitHub has no checks for the newest commit.")
            checks.value.sortedBy { it.outcome.ordinal }.forEach { check ->
                DropdownMenuItem(
                    text = { CheckMenuText(check) },
                    leadingIcon = { OutcomeDot(check.outcome) },
                    enabled = check.url != null,
                    onClick = { onDismiss(); check.url?.let(uriHandler::openUri) },
                )
            }
        }
    }
}

@Composable
private fun MenuNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun CheckMenuText(check: Check) {
    Column {
        Text(check.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        check.summary?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FixErrorsButton(isPreparing: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !isPreparing, contentPadding = COMPACT_BUTTON_PADDING) {
        if (isPreparing) CircularProgressIndicator(modifier = Modifier.padding(end = 6.dp).size(12.dp), strokeWidth = 2.dp)
        Text("Fix errors")
    }
}

/** GitHub cannot merge a pull request with conflicts, so the bar says so instead of offering a merge that fails. */
@Composable
private fun MergeControl(mergeability: Mergeability, onClick: () -> Unit) {
    if (mergeability == Mergeability.CONFLICTING) {
        Text("Conflicts", style = MaterialTheme.typography.labelLarge, color = diffColors().removed, modifier = Modifier.padding(horizontal = 8.dp))
        return
    }
    FilledTonalButton(onClick = onClick, contentPadding = COMPACT_BUTTON_PADDING) { Text("Merge") }
}

@Composable
internal fun OutcomeDot(outcome: CheckOutcome) {
    Box(modifier = Modifier.size(10.dp).background(outcomeColor(outcome), CircleShape))
}

@Composable
private fun outcomeColor(outcome: CheckOutcome): Color {
    val colors = diffColors()
    return when (outcome) {
        CheckOutcome.PASSED -> colors.added
        CheckOutcome.FAILED -> colors.removed
        CheckOutcome.PENDING -> colors.modified
        CheckOutcome.NEUTRAL -> colors.neutral
    }
}

/** Failed first, then running, then passed. A pull request with no checks, or only skipped checks, is neutral. */
internal fun overallOutcome(checks: List<Check>): CheckOutcome = when {
    checks.any { it.outcome == CheckOutcome.FAILED } -> CheckOutcome.FAILED
    checks.any { it.outcome == CheckOutcome.PENDING } -> CheckOutcome.PENDING
    checks.any { it.outcome == CheckOutcome.PASSED } -> CheckOutcome.PASSED
    else -> CheckOutcome.NEUTRAL
}

internal fun checksLabel(checks: List<Check>): String {
    val failed = checks.count { it.outcome == CheckOutcome.FAILED }
    val pending = checks.count { it.outcome == CheckOutcome.PENDING }
    val passed = checks.count { it.outcome == CheckOutcome.PASSED }
    return when {
        checks.isEmpty() -> "No checks"
        failed > 0 -> "$failed of ${checkCount(checks.size)} failed"
        pending > 0 -> "$pending of ${checkCount(checks.size)} running"
        passed == checks.size -> "${checkCount(passed)} passed"
        else -> "${checkCount(passed)} passed, ${checks.size - passed} skipped"
    }
}

private fun checkCount(count: Int): String = if (count == 1) "1 check" else "$count checks"

private val COMPACT_BUTTON_PADDING = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
