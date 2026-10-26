package build.conductor.android.client.data.github

import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The stack positions of the listed workspaces, by workspace id. */
interface StackPositions {
    val positions: StateFlow<Map<String, StackPosition>>

    /** Reads the positions of [workspaces] again. The earlier positions show until GitHub answers. */
    fun load(workspaces: List<Workspace>)

    object None : StackPositions {
        override val positions: StateFlow<Map<String, StackPosition>> = MutableStateFlow(emptyMap())
        override fun load(workspaces: List<Workspace>) = Unit
    }
}

/**
 * Reads the positions from GitHub, one load for each repository. Every list screen shares one instance, so a list shows the positions
 * that another list found. Without a token, a workspace shows no position. When GitHub fails, the workspaces of that repository show none.
 */
class GitHubStackPositions(private val github: GitHubRepository, isConnected: Flow<Boolean>, private val scope: CoroutineScope) : StackPositions {
    private val state = MutableStateFlow<Map<String, StackPosition>>(emptyMap())
    override val positions: StateFlow<Map<String, StackPosition>> = state.asStateFlow()
    private val loads = SupervisorJob(scope.coroutineContext[Job])
    private var latestWorkspaces: List<Workspace> = emptyList()
    private var isConnectedNow = false

    init {
        scope.launch {
            isConnected.distinctUntilChanged().collect { connected ->
                isConnectedNow = connected
                if (connected) load(latestWorkspaces) else disconnect()
            }
        }
    }

    override fun load(workspaces: List<Workspace>) {
        latestWorkspaces = workspaces
        if (!isConnectedNow) return
        workspaces.groupBy { RepositoryName.from(it.repoUrl) }.forEach { (repository, members) ->
            if (repository != null) scope.launch(loads) { showPositions(repository, members) }
        }
    }

    private suspend fun showPositions(repository: RepositoryName, workspaces: List<Workspace>) {
        val workspacesByKey = workspaces.associateBy { WorkspaceKey(it.number, it.name) }
        val found = try {
            github.stackPositions(repository, workspacesByKey.keys.toList())
        } catch (exception: ApiException) {
            emptyMap()
        }
        val positions = found.mapKeys { (key, _) -> workspacesByKey.getValue(key).id }
        state.update { current -> current - workspaces.map { it.id }.toSet() + positions }
    }

    private fun disconnect() {
        loads.cancelChildren()
        state.value = emptyMap()
    }
}
