package build.conductor.android.client.ui.workspaces

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import build.conductor.android.client.R
import build.conductor.android.client.data.WorkspaceArchiver
import build.conductor.android.client.data.WorkspaceState
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.data.github.StackPosition
import kotlinx.coroutines.flow.Flow

/** What a list screen needs to archive a workspace with a swipe and to undo it. */
interface WorkspaceArchiving {
    val archiveFailures: Flow<String>
    fun archive(workspace: Workspace)
    fun undoArchive()
    fun confirmArchive()
}

/** Lets a view model hand its list actions to the [WorkspaceArchiver] that every screen shares. */
class SharedWorkspaceArchiving(private val archiver: WorkspaceArchiver) : WorkspaceArchiving {
    override val archiveFailures: Flow<String> = archiver.failureMessages
    override fun archive(workspace: Workspace) = archiver.archive(workspace)
    override fun undoArchive() = archiver.undo()
    override fun confirmArchive() = archiver.confirm()
}

/** Shows "Undo" while the undo window of [undoable] is open, and a message for each failed archive. */
@Composable
fun ArchiveSnackbars(undoable: Workspace?, archiving: WorkspaceArchiving, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(undoable?.id) {
        if (undoable == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar("Archived \"${undoable.name}\"", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
        when (result) {
            SnackbarResult.ActionPerformed -> archiving.undoArchive()
            SnackbarResult.Dismissed -> archiving.confirmArchive()
        }
    }
    LaunchedEffect(archiving) {
        archiving.archiveFailures.collect { snackbarHostState.showSnackbar(it) }
    }
}

/** A swipe towards the start archives the workspace. Screen readers get the same action from the actions menu. */
@Composable
fun SwipeToArchiveRow(workspace: Workspace, stackPosition: StackPosition?, onClick: () -> Unit, onArchive: () -> Unit) {
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
        WorkspaceRow(workspace, stackPosition, onClick)
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
