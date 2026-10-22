package build.conductor.android.client.data

import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArchiveState(
    /** Workspaces that the user archived and that the server did not confirm yet. Every list hides them. */
    val pendingIds: Set<String> = emptySet(),
    /** Workspaces that the server archived since the app started. */
    val archivedIds: Set<String> = emptySet(),
    /** The last archived workspace, while its undo window is open. */
    val undoable: Workspace? = null,
)

/**
 * Every screen shares one archiver, so a workspace archived on one screen leaves every list. [scope] outlives the screens; the app
 * runs it on the main thread, so the undo window and the user's taps do not race.
 */
class WorkspaceArchiver(private val repository: ConductorRepository, private val scope: CoroutineScope) {
    private val state = MutableStateFlow(ArchiveState())
    val archiveState: StateFlow<ArchiveState> = state.asStateFlow()
    private val failures = Channel<String>(Channel.BUFFERED)

    /** One message for each archive call that failed. A message waits until a screen shows it. */
    val failureMessages: Flow<String> = failures.receiveAsFlow()
    private var undoWindowJob: Job? = null

    /** Hides the workspace now and archives it when the undo window closes. A workspace in an open window is archived at once. */
    fun archive(workspace: Workspace) {
        if (workspace.id in state.value.pendingIds || WorkspaceState.from(workspace.state) == WorkspaceState.ARCHIVED) return
        confirm()
        state.update { it.copy(pendingIds = it.pendingIds + workspace.id, undoable = workspace) }
        undoWindowJob = scope.launch {
            delay(UNDO_WINDOW_MILLIS)
            // The timer must not cancel its own job, so it takes the workspace without closeUndoWindow.
            takeUndoable(workspace.id)?.let { send(it) }
        }
    }

    fun undo() {
        val workspace = closeUndoWindow() ?: return
        state.update { it.copy(pendingIds = it.pendingIds - workspace.id) }
    }

    /** Closes the undo window early and archives the workspace now. */
    fun confirm() {
        val workspace = closeUndoWindow() ?: return
        scope.launch { send(workspace) }
    }

    private fun closeUndoWindow(): Workspace? {
        undoWindowJob?.cancel()
        return takeUndoable(state.value.undoable?.id ?: return null)
    }

    /** Clears the undo window of [workspaceId] in one step, so only one caller gets the workspace. */
    private fun takeUndoable(workspaceId: String): Workspace? {
        var taken: Workspace? = null
        state.update { current ->
            taken = current.undoable?.takeIf { it.id == workspaceId }
            if (taken == null) current else current.copy(undoable = null)
        }
        return taken
    }

    private suspend fun send(workspace: Workspace) {
        val isArchived = try {
            archiveOrCheck(workspace.id)
            true
        } catch (exception: ApiException) {
            failures.trySend("Could not archive \"${workspace.name}\". ${exception.message}")
            false
        }
        state.update { current ->
            current.copy(
                pendingIds = current.pendingIds - workspace.id,
                archivedIds = if (isArchived) current.archivedIds + workspace.id else current.archivedIds,
            )
        }
    }

    /** The server can finish an archive after the connection drops, so a lost answer is checked with a read. */
    private suspend fun archiveOrCheck(workspaceId: String) {
        try {
            repository.archiveWorkspace(workspaceId)
        } catch (exception: ApiException.Offline) {
            if (!isArchivedOnServer(workspaceId)) throw exception
        }
    }

    private suspend fun isArchivedOnServer(workspaceId: String): Boolean = try {
        WorkspaceState.from(repository.workspace(workspaceId).state) == WorkspaceState.ARCHIVED
    } catch (_: ApiException) {
        false
    }

    companion object {
        const val UNDO_WINDOW_MILLIS = 5_000L
    }
}

/** The list that the user sees: pending archives are hidden, and a finished archive leaves the list or shows as archived. */
fun ArchiveState.applyTo(workspaces: List<Workspace>, isShowingArchived: Boolean): List<Workspace> = workspaces
    .filterNot { it.id in pendingIds || (it.id in archivedIds && !isShowingArchived) }
    .map { if (it.id in archivedIds) it.copy(state = ARCHIVED_STATE, lifecycleStep = null) else it }

private val ARCHIVED_STATE = WorkspaceState.ARCHIVED.name.lowercase()
