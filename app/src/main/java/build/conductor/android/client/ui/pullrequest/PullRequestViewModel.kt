package build.conductor.android.client.ui.pullrequest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.data.github.FileChange
import build.conductor.android.client.data.github.GitHubRepository
import build.conductor.android.client.data.github.PullRequestStack
import build.conductor.android.client.data.github.PullRequestSummary
import build.conductor.android.client.data.github.RepositoryName
import build.conductor.android.client.data.github.WorkspaceKey
import build.conductor.android.client.ui.components.LoadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
)

val PullRequestUiState.selectedTier: PullRequestSummary?
    get() = (lookup as? StackLookup.Found)?.stack?.tiers?.firstOrNull { it.number == selectedNumber }

/** Finds the pull request of [workspace], the stack that holds it, and the files that each tier changes. */
class PullRequestViewModel(
    private val github: GitHubRepository,
    isConnected: Flow<Boolean>,
    workspace: Workspace,
) : ViewModel() {
    private val repository = RepositoryName.from(workspace.repoUrl)
    private val workspaceKey = WorkspaceKey(workspace.number, workspace.name)
    private val state = MutableStateFlow(PullRequestUiState())
    val uiState: StateFlow<PullRequestUiState> = state.asStateFlow()
    private var lookupJob: Job? = null
    private var isConnectedNow = false

    init {
        viewModelScope.launch {
            isConnected.distinctUntilChanged().collect { connected ->
                isConnectedNow = connected
                if (connected) lookUp() else state.update { PullRequestUiState(lookup = StackLookup.NotConnected) }
            }
        }
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
        if (state.value.lookup !is StackLookup.Found) state.update { it.copy(lookup = StackLookup.Loading) }
        lookupJob = viewModelScope.launch { showStack(repository) }
    }

    private suspend fun showStack(repository: RepositoryName) {
        val lookup = try {
            github.workspaceStack(repository, workspaceKey)?.let { StackLookup.Found(it) } ?: StackLookup.NotFound
        } catch (exception: ApiException) {
            StackLookup.Failed(exception.message.orEmpty(), exception is ApiException.Unauthorized)
        }
        state.update { current -> current.copy(lookup = lookup, selectedNumber = keptSelection(current.selectedNumber, lookup), changes = emptyMap()) }
        if (state.value.isShowingChanges) loadChanges()
    }

    private fun keptSelection(selectedNumber: Int?, lookup: StackLookup): Int? {
        val stack = (lookup as? StackLookup.Found)?.stack ?: return null
        return selectedNumber?.takeIf { number -> stack.tiers.any { it.number == number } } ?: stack.workspaceNumber
    }

    fun selectTier(number: Int) {
        state.update { it.copy(selectedNumber = number, expandedFiles = emptySet()) }
        if (state.value.isShowingChanges) loadChanges()
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
        viewModelScope.launch {
            val loaded = try {
                val files = github.changedFiles(repository, number)
                state.update { it.copy(collapsedFolders = it.collapsedFolders + (number to initiallyCollapsedFolders(files))) }
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
}
