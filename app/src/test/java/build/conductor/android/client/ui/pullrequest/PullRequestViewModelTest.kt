@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.ui.pullrequest

import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.github.ChangeKind
import build.conductor.android.client.data.github.DiffCount
import build.conductor.android.client.data.github.FileChange
import build.conductor.android.client.data.github.GitHubRepository
import build.conductor.android.client.data.github.GitHubUser
import build.conductor.android.client.data.github.PullRequestStack
import build.conductor.android.client.data.github.PullRequestState
import build.conductor.android.client.data.github.PullRequestSummary
import build.conductor.android.client.data.github.RepositoryName
import build.conductor.android.client.data.github.StackPosition
import build.conductor.android.client.data.github.WorkspaceKey
import build.conductor.android.client.ui.components.LoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PullRequestViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val github = FakeGitHubRepository()
    private val isConnected = MutableStateFlow(true)
    private val workspace = workspace("w1", "push").copy(number = 772)

    @Test
    fun `without a token the bar asks for one, and a saved token finds the stack`() = runTest {
        isConnected.value = false
        val viewModel = PullRequestViewModel(github, isConnected, workspace)
        advanceUntilIdle()
        assertEquals(StackLookup.NotConnected, viewModel.uiState.value.lookup)

        isConnected.value = true
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.lookup is StackLookup.Found)
        assertEquals(13, viewModel.uiState.value.selectedNumber)
        assertEquals(WorkspaceKey(772, "push"), github.lookups.single().second)
    }

    @Test
    fun `a workspace whose repository is not on GitHub shows no bar`() = runTest {
        val viewModel = PullRequestViewModel(github, isConnected, workspace.copy(repoUrl = "https://gitlab.com/example-org/example-repo"))
        advanceUntilIdle()

        assertEquals(StackLookup.NotOnGitHub, viewModel.uiState.value.lookup)
    }

    @Test
    fun `the changes of a tier load once, and another tier loads its own`() = runTest {
        val viewModel = PullRequestViewModel(github, isConnected, workspace)
        advanceUntilIdle()

        viewModel.showChanges()
        viewModel.showChanges()
        advanceUntilIdle()
        viewModel.selectTier(14)
        advanceUntilIdle()

        assertEquals(listOf(13, 14), github.fileRequests)
        assertTrue(viewModel.uiState.value.changes[14] is LoadState.Loaded)
        assertEquals(14, viewModel.uiState.value.selectedTier?.number)
    }

    @Test
    fun `a refresh keeps the selected tier`() = runTest {
        val viewModel = PullRequestViewModel(github, isConnected, workspace)
        advanceUntilIdle()
        viewModel.selectTier(14)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(14, viewModel.uiState.value.selectedNumber)
        assertEquals(2, github.lookups.size)
    }

    @Test
    fun `a rejected token sends the user to settings, and no pull request says so`() = runTest {
        github.failure = ApiException.Unauthorized("GitHub rejected the token.")
        val viewModel = PullRequestViewModel(github, isConnected, workspace)
        advanceUntilIdle()
        assertEquals(StackLookup.Failed("GitHub rejected the token.", isTokenRejected = true), viewModel.uiState.value.lookup)

        github.failure = null
        github.stack = null
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(StackLookup.NotFound, viewModel.uiState.value.lookup)
    }

    @Test
    fun `folders collapse and expand for the selected tier`() = runTest {
        val viewModel = PullRequestViewModel(github, isConnected, workspace)
        advanceUntilIdle()
        viewModel.showChanges()
        advanceUntilIdle()

        viewModel.toggleFolder("app")
        assertEquals(setOf("app"), viewModel.uiState.value.collapsedFolders[13])
        viewModel.toggleFolder("app")
        assertEquals(emptySet<String>(), viewModel.uiState.value.collapsedFolders[13])
    }
}

private class FakeGitHubRepository : GitHubRepository {
    var stack: PullRequestStack? = PullRequestStack(listOf(summary(14), summary(13)), baseRef = "main", workspaceNumber = 13)
    var failure: ApiException? = null
    val lookups = mutableListOf<Pair<RepositoryName, WorkspaceKey>>()
    val fileRequests = mutableListOf<Int>()

    override suspend fun testToken(token: String) = GitHubUser("octocat")

    override suspend fun workspaceStack(repository: RepositoryName, workspace: WorkspaceKey): PullRequestStack? {
        lookups += repository to workspace
        failure?.let { throw it }
        return stack
    }

    override suspend fun stackPositions(repository: RepositoryName, workspaces: List<WorkspaceKey>): Map<WorkspaceKey, StackPosition> = emptyMap()

    override suspend fun changedFiles(repository: RepositoryName, number: Int): List<FileChange> {
        fileRequests += number
        return listOf(FileChange("app/Main.kt", ChangeKind.MODIFIED, DiffCount(1, 1), patch = null, previousPath = null))
    }
}

private fun summary(number: Int) = PullRequestSummary(
    number = number,
    title = "PR $number",
    state = PullRequestState.OPEN,
    headRef = "branch-$number",
    baseRef = "main",
    url = "https://github.com/example-org/example-repo/pull/$number",
    diff = DiffCount(10, 2),
    changedFiles = 1,
)
