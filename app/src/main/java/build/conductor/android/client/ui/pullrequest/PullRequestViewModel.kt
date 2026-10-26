package build.conductor.android.client.ui.pullrequest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.data.github.Check
import build.conductor.android.client.data.github.CheckOutcome
import build.conductor.android.client.data.github.FileChange
import build.conductor.android.client.data.github.GitHubRepository
import build.conductor.android.client.data.github.MergeMethod
import build.conductor.android.client.data.github.MergeRequest
import build.conductor.android.client.data.github.Mergeability
import build.conductor.android.client.data.github.PullRequestActions
import build.conductor.android.client.data.github.PullRequestStack
import build.conductor.android.client.data.github.PullRequestState
import build.conductor.android.client.data.github.PullRequestSummary
import build.conductor.android.client.data.github.RepositoryName
import build.conductor.android.client.data.github.WorkspaceKey
import build.conductor.android.client.data.github.fixErrorsPrompt
import build.conductor.android.client.ui.components.LoadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the app knows about the pull request of a workspace. */
sealed interface StackLookup {
    /** The user saved no GitHub token. */
    data object NotConnected : StackLookup

    /** The repository is not on github.com. */
    data object NotOnGitHub : StackLookup
    data object Loading : StackLookup
    data object NotFound : StackLookup
    data class Failed(val message: String, val isTokenRejected: Boolean) : StackLookup
    data class Found(val stack: PullRequestStack) : StackLookup
}

data class PullRequestUiState(
    val lookup: StackLookup = StackLookup.Loading,
    /** The tier that the bar and the changes sheet show. It starts at the pull request of the workspace. */
    val selectedNumber: Int? = null,
    /** The changed files of each tier that the user opened. */
    val changes: Map<Int, LoadState<List<FileChange>>> = emptyMap(),
    val isShowingChanges: Boolean = false,
    val grouping: ChangeGrouping = ChangeGrouping.FOLDERS,
    val collapsedFolders: Map<Int, Set<String>> = emptyMap(),
    val expandedFiles: Set<String> = emptySet(),
    /** The checks of the selected tier when it is open or a draft. A refresh keeps the old checks on screen while it reads them again. */
    val checks: Map<Int, LoadState<List<Check>>> = emptyMap(),
    val isPreparingFix: Boolean = false,
    /** The open merge dialog, or null. */
    val merge: MergeDialogState? = null,
)

val PullRequestUiState.selectedTier: PullRequestSummary?
    get() = (lookup as? StackLookup.Found)?.stack?.tiers?.firstOrNull { it.number == selectedNumber }

/** The checks of the selected tier, or an empty list until they load. */
val PullRequestUiState.selectedChecks: List<Check>
    get() = (checks[selectedNumber] as? LoadState.Loaded)?.value.orEmpty()

/** [method] is null until the repository's merge methods load. */
data class MergeDialogState(
    val tier: PullRequestSummary,
    val baseRef: String,
    val methods: LoadState<List<MergeMethod>> = LoadState.Loading,
    val method: MergeMethod? = null,
    val isMerging: Boolean = false,
    val error: String? = null,
)

sealed interface PullRequestEvent {
    /** The Fix errors prompt, for the open session of the workspace. */
    data class SendPrompt(val text: String) : PullRequestEvent
    data class ShowMessage(val text: String) : PullRequestEvent
}

/** The GitHub parts that the pull request bar reads and writes. */
class GitHubAccess(val repository: GitHubRepository, val actions: PullRequestActions, val isConnected: Flow<Boolean>)

/** Finds the pull request of [workspace], the stack that holds it, the files that each tier changes and its checks, and merges it. */
class PullRequestViewModel(github: GitHubAccess, workspace: Workspace) : ViewModel() {
    private val pullRequests = github.repository
    private val actions = github.actions
    private val repository = RepositoryName.from(workspace.repoUrl)
    private val workspaceKey = WorkspaceKey(workspace.number, workspace.name)
    private val state = MutableStateFlow(PullRequestUiState())
    val uiState: StateFlow<PullRequestUiState> = state.asStateFlow()
    private val events = Channel<PullRequestEvent>(Channel.BUFFERED)
    val eventFlow: Flow<PullRequestEvent> = events.receiveAsFlow()
    private var lookupJob: Job? = null
    private var checksJob: Job? = null
    private var pollJob: Job? = null
    private val changeJobs = mutableMapOf<Int, Job>()
    private var isConnectedNow = false
    private var isVisible = false

    init {
        viewModelScope.launch {
            github.isConnected.distinctUntilChanged().collect { connected ->
                isConnectedNow = connected
                if (connected) lookUp() else disconnect()
            }
        }
    }

    private fun disconnect() {
        lookupJob?.cancel()
        checksJob?.cancel()
        pollJob?.cancel()
        cancelChangeLoads()
        state.update { PullRequestUiState(lookup = StackLookup.NotConnected) }
    }

    private fun cancelChangeLoads() {
        changeJobs.values.forEach { it.cancel() }
        changeJobs.clear()
    }

    /** Reads the stack again, and reads it every 30 seconds while a check runs or GitHub computes the merge state. */
    fun onVisible() {
        isVisible = true
        refresh()
    }

    fun onHidden() {
        isVisible = false
        pollJob?.cancel()
    }

    /** Reads the stack again; the selected tier stays when it is still in the stack. */
    fun refresh() {
        if (isConnectedNow && state.value.lookup !is StackLookup.Loading) lookUp()
    }

    private fun lookUp() {
        if (repository == null) {
            state.update { it.copy(lookup = StackLookup.NotOnGitHub) }
            return
        }
        lookupJob?.cancel()
        pollJob?.cancel()
        if (state.value.lookup !is StackLookup.Found) state.update { it.copy(lookup = StackLookup.Loading) }
        lookupJob = viewModelScope.launch { showStack(repository) }
    }

    private suspend fun showStack(repository: RepositoryName) {
        val lookup = try {
            pullRequests.workspaceStack(repository, workspaceKey)?.let { StackLookup.Found(it) } ?: StackLookup.NotFound
        } catch (exception: ApiException) {
            StackLookup.Failed(exception.message.orEmpty(), exception is ApiException.Unauthorized)
        }
        cancelChangeLoads()
        state.update { current ->
            val selectedNumber = keptSelection(current.selectedNumber, lookup)
            current.copy(lookup = lookup, selectedNumber = selectedNumber, changes = emptyMap(), checks = current.checks.filterKeys { it == selectedNumber })
        }
        if (state.value.isShowingChanges) loadChanges()
        loadChecks()
    }

    private fun keptSelection(selectedNumber: Int?, lookup: StackLookup): Int? {
        val stack = (lookup as? StackLookup.Found)?.stack ?: return null
        return selectedNumber?.takeIf { number -> stack.tiers.any { it.number == number } } ?: stack.workspaceNumber
    }

    fun selectTier(number: Int) {
        state.update { it.copy(selectedNumber = number, expandedFiles = emptySet()) }
        if (state.value.isShowingChanges) loadChanges()
        loadChecks()
    }

    private fun loadChecks() {
        val tier = state.value.selectedTier?.takeIf { it.state == PullRequestState.OPEN || it.state == PullRequestState.DRAFT } ?: return
        val repository = repository ?: return
        checksJob?.cancel()
        if (state.value.checks[tier.number] !is LoadState.Loaded) state.update { it.copy(checks = it.checks + (tier.number to LoadState.Loading)) }
        checksJob = viewModelScope.launch {
            val loaded = try {
                LoadState.Loaded(actions.checks(repository, tier.headSha))
            } catch (exception: ApiException) {
                LoadState.Failed(exception.message.orEmpty(), exception.isRetryable)
            }
            state.update { it.copy(checks = it.checks + (tier.number to loaded)) }
            pollIfWaiting()
        }
    }

    private fun pollIfWaiting() {
        pollJob?.cancel()
        if (!isVisible || !isWaitingForGitHub()) return
        pollJob = viewModelScope.launch {
            delay(POLL_INTERVAL_MILLIS)
            refresh()
        }
    }

    private fun isWaitingForGitHub(): Boolean {
        val tier = state.value.selectedTier ?: return false
        val isCheckRunning = state.value.selectedChecks.any { it.outcome == CheckOutcome.PENDING }
        return isCheckRunning || (tier.state == PullRequestState.OPEN && tier.mergeability == Mergeability.UNKNOWN)
    }

    /** Reads the logs of the failed checks, and sends a prompt that asks the agent to fix them. */
    fun fixErrors() {
        val tier = state.value.selectedTier ?: return
        val repository = repository ?: return
        val failedChecks = state.value.selectedChecks.filter { it.outcome == CheckOutcome.FAILED }
        if (failedChecks.isEmpty() || state.value.isPreparingFix) return
        state.update { it.copy(isPreparingFix = true) }
        viewModelScope.launch {
            try {
                events.send(PullRequestEvent.SendPrompt(fixErrorsPrompt(tier, actions.checkFailures(repository, failedChecks))))
            } catch (exception: ApiException) {
                events.send(PullRequestEvent.ShowMessage(exception.message ?: "The app could not read the failed checks."))
            }
            state.update { it.copy(isPreparingFix = false) }
        }
    }

    fun requestMerge() {
        val stack = (state.value.lookup as? StackLookup.Found)?.stack ?: return
        val tier = state.value.selectedTier?.takeIf { stack.canMerge(it) } ?: return
        state.update { it.copy(merge = MergeDialogState(tier, stack.baseRef)) }
        loadMergeMethods()
    }

    fun retryMergeMethods() {
        state.update { current -> current.copy(merge = current.merge?.copy(methods = LoadState.Loading)) }
        loadMergeMethods()
    }

    private fun loadMergeMethods() {
        val repository = repository ?: return
        viewModelScope.launch {
            val methods = try {
                LoadState.Loaded(actions.mergeMethods(repository))
            } catch (exception: ApiException) {
                LoadState.Failed(exception.message.orEmpty(), exception.isRetryable)
            }
            state.update { current -> current.copy(merge = current.merge?.withMethods(methods)) }
        }
    }

    private fun MergeDialogState.withMethods(methods: LoadState<List<MergeMethod>>) =
        copy(methods = methods, method = (methods as? LoadState.Loaded)?.value?.firstOrNull())

    fun selectMergeMethod(method: MergeMethod) = state.update { current -> current.copy(merge = current.merge?.copy(method = method)) }

    fun dismissMerge() = state.update { current -> if (current.merge?.isMerging == true) current else current.copy(merge = null) }

    fun confirmMerge() {
        val dialog = state.value.merge?.takeIf { !it.isMerging } ?: return
        val method = dialog.method ?: return
        val repository = repository ?: return
        state.update { it.copy(merge = dialog.copy(isMerging = true, error = null)) }
        viewModelScope.launch {
            try {
                actions.merge(repository, MergeRequest(dialog.tier.number, dialog.tier.headSha, method))
                state.update { it.copy(merge = null) }
                events.send(PullRequestEvent.ShowMessage("Merged #${dialog.tier.number} into ${dialog.baseRef}"))
                lookUp()
            } catch (exception: ApiException) {
                state.update { current -> current.copy(merge = current.merge?.copy(isMerging = false, error = exception.message)) }
            }
        }
    }

    fun showChanges() {
        state.update { it.copy(isShowingChanges = true) }
        loadChanges()
    }

    fun hideChanges() = state.update { it.copy(isShowingChanges = false) }

    private fun loadChanges() {
        val number = state.value.selectedNumber ?: return
        val repository = repository ?: return
        if (state.value.changes[number].let { it is LoadState.Loaded || it is LoadState.Loading }) return
        state.update { it.copy(changes = it.changes + (number to LoadState.Loading)) }
        changeJobs[number] = viewModelScope.launch {
            val loaded = try {
                val files = pullRequests.changedFiles(repository, number)
                state.update { if (number in it.collapsedFolders) it else it.copy(collapsedFolders = it.collapsedFolders + (number to initiallyCollapsedFolders(files))) }
                LoadState.Loaded(files)
            } catch (exception: ApiException) {
                LoadState.Failed(exception.message.orEmpty(), exception.isRetryable)
            }
            state.update { it.copy(changes = it.changes + (number to loaded)) }
        }
    }

    fun retryChanges() {
        val number = state.value.selectedNumber ?: return
        state.update { it.copy(changes = it.changes - number) }
        loadChanges()
    }

    fun selectGrouping(grouping: ChangeGrouping) = state.update { it.copy(grouping = grouping) }

    fun toggleFolder(path: String) {
        val number = state.value.selectedNumber ?: return
        state.update { current ->
            val collapsed = current.collapsedFolders[number].orEmpty()
            current.copy(collapsedFolders = current.collapsedFolders + (number to if (path in collapsed) collapsed - path else collapsed + path))
        }
    }

    fun toggleFile(path: String) = state.update { current ->
        current.copy(expandedFiles = if (path in current.expandedFiles) current.expandedFiles - path else current.expandedFiles + path)
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 30_000L
    }
}
