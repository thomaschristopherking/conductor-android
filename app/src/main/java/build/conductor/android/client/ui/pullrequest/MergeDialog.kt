package build.conductor.android.client.ui.pullrequest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import build.conductor.android.client.data.github.Check
import build.conductor.android.client.data.github.CheckOutcome
import build.conductor.android.client.data.github.MergeMethod
import build.conductor.android.client.data.github.Mergeability
import build.conductor.android.client.data.github.PullRequestSummary
import build.conductor.android.client.ui.components.LoadState

/** Asks for the merge method, and shows what can stop the merge before the user confirms it. */
@Composable
internal fun MergeDialog(dialog: MergeDialogState, checks: List<Check>, viewModel: PullRequestViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissMerge,
        title = { Text("Merge #${dialog.tier.number} into ${dialog.baseRef}?") },
        text = { MergeDialogBody(dialog, mergeWarnings(dialog.tier, checks), viewModel) },
        confirmButton = {
            TextButton(onClick = viewModel::confirmMerge, enabled = dialog.method != null && !dialog.isMerging) {
                if (dialog.isMerging) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(14.dp), strokeWidth = 2.dp)
                Text(if (dialog.isMerging) "Merging…" else "Merge")
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissMerge, enabled = !dialog.isMerging) { Text("Cancel") } },
    )
}

@Composable
private fun MergeDialogBody(dialog: MergeDialogState, warnings: List<String>, viewModel: PullRequestViewModel) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(dialog.tier.title, style = MaterialTheme.typography.bodyMedium)
        warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = diffColors().modified) }
        when (val methods = dialog.methods) {
            LoadState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            is LoadState.Failed -> MethodsError(methods.message, viewModel::retryMergeMethods)
            is LoadState.Loaded -> MethodChoices(methods.value, dialog.method, enabled = !dialog.isMerging, onSelect = viewModel::selectMergeMethod)
        }
        dialog.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MethodsError(message: String, onRetry: () -> Unit) {
    Column {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun MethodChoices(methods: List<MergeMethod>, selected: MergeMethod?, enabled: Boolean, onSelect: (MergeMethod) -> Unit) {
    if (methods.isEmpty()) {
        Text("The repository allows no merge method.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        return
    }
    Column(modifier = Modifier.selectableGroup()) {
        methods.forEach { method ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(selected = method == selected, enabled = enabled, role = Role.RadioButton) { onSelect(method) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = method == selected, onClick = null, enabled = enabled)
                Text(method.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
            }
        }
    }
}

/** Each reason that GitHub can refuse the merge, or that the user can regret it. GitHub decides; the dialog does not block the merge. */
internal fun mergeWarnings(tier: PullRequestSummary, checks: List<Check>): List<String> = buildList {
    if (checks.any { it.outcome == CheckOutcome.FAILED }) add("Some checks failed.")
    if (checks.any { it.outcome == CheckOutcome.PENDING }) add("Some checks still run.")
    when (tier.mergeability) {
        Mergeability.BLOCKED -> add("GitHub blocks the merge until a required review or check passes. An admin can still merge.")
        Mergeability.BEHIND -> add("The branch is behind ${tier.baseRef}. GitHub can refuse the merge.")
        Mergeability.UNKNOWN -> add("GitHub has not finished its merge check.")
        Mergeability.UNSTABLE -> if (isEmpty()) add("A check that is not required failed.")
        Mergeability.READY, Mergeability.CONFLICTING -> Unit
    }
}
