@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.ui.pullrequest

import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.github.AppRelease
import build.conductor.android.client.data.github.ChangeKind
import build.conductor.android.client.data.github.Check
import build.conductor.android.client.data.github.CheckFailure
import build.conductor.android.client.data.github.CheckOutcome
import build.conductor.android.client.data.github.DiffCount
import build.conductor.android.client.data.github.FileChange
import build.conductor.android.client.data.github.GitHubRepository
import build.conductor.android.client.data.github.GitHubUser
import build.conductor.android.client.data.github.MergeMethod
import build.conductor.android.client.data.github.MergeRequest
import build.conductor.android.client.data.github.Mergeability
import build.conductor.android.client.data.github.PullRequestActions
import build.conductor.android.client.data.github.PullRequestStack
import build.conductor.android.client.data.github.PullRequestState
import build.conductor.android.client.data.github.PullRequestSummary
import build.conductor.android.client.data.github.RepositoryName
import build.conductor.android.client.data.github.StackPosition
import build.conductor.android.client.data.github.WorkspaceKey
import build.conductor.android.client.ui.components.LoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PullRequestViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val github = FakeGitHubRepository()
    private val actions = FakePullRequestActions()
    private val isConnected = MutableStateFlow(true)
    private val access = GitHubAccess(github, actions, isConnected)
    private val workspace = workspace("w1", "push").copy(number = 772)

    @Test
    fun `without a token the bar asks for one, and a saved token finds the stack`() = runTest {
        isConnected.value = false
        val viewModel = PullRequestViewModel(access, workspace)
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
        val viewModel = PullRequestViewModel(access, workspace.copy(repoUrl = "https://gitlab.com/example-org/example-repo"))
        advanceUntilIdle()

        assertEquals(StackLookup.NotOnGitHub, viewModel.uiState.value.lookup)
    }

    @Test
    fun `the changes of a tier load once, and another tier loads its own`() = runTest {
        val viewModel = PullRequestViewModel(access, workspace)
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
        val viewModel = PullRequestViewModel(access, workspace)
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
        val viewModel = PullRequestViewModel(access, workspace)
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
        val viewModel = PullRequestViewModel(access, workspace)
        advanceUntilIdle()
        viewModel.showChanges()
        advanceUntilIdle()

        viewModel.toggleFolder("app")
        assertEquals(setOf("app"), viewModel.uiState.value.collapsedFolders[13])
        viewModel.toggleFolder("app")
        assertEquals(emptySet<String>(), viewModel.uiState.value.collapsedFolders[13])
    }

    @Test
    fun `the checks of the selected tier load with the stack, and another tier loads its own`() = runTest {
        val viewModel = PullRequestViewModel(access, workspace)
        advanceUntilIdle()
        assertEquals(listOf("sha-13"), actions.checkRequests)
        assertEquals(LoadState.Loaded(actions.checks), viewModel.uiState.value.checks[13])

        viewModel.selectTier(14)
        advanceUntilIdle()

        assertEquals(listOf("sha-13", "sha-14"), actions.checkRequests)
    }

    @Test
    fun `a merged tier has no checks to read`() = runTest {
        github.stack = PullRequestStack(listOf(summary(13).copy(state = PullRequestState.MERGED)), baseRef = "main", workspaceNumber = 13)
        PullRequestViewModel(access, workspace)
        advanceUntilIdle()

        assertTrue(actions.checkRequests.isEmpty())
    }

    @Test
    fun `fix errors reads the failed checks only and sends a prompt with their logs`() = runTest {
        actions.checks = listOf(check("build", CheckOutcome.FAILED), check("lint", CheckOutcome.PASSED))
        val viewModel = PullRequestViewModel(access, workspace)
        advanceUntilIdle()

        viewModel.fixErrors()
        val event = viewModel.eventFlow.first()

        assertEquals(listOf("build"), actions.failureRequests.single().map { it.name })
        val prompt = (event as PullRequestEvent.SendPrompt).text
        assertTrue(prompt, prompt.startsWith("The checks of pull request #13, \"PR 13\", failed on commit sha-13."))
        assertTrue(prompt, "## build" in prompt && "error: build failed" in prompt && "lint" !in prompt)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isPreparingFix)
    }

    @Test
    fun `fix errors does nothing when no check failed`() = runTest {
        val viewModel = PullRequestViewModel(access, workspace)
        advanceUntilIdle()

        viewModel.fixErrors()
        advanceUntilIdle()

        assertTrue(actions.failureRequests.isEmpty())
    }

    @Test
    fun `only the bottom tier of the stack opens the merge dialog`() = runTest {
        val viewModel = PullRequestViewModel(access, workspace)
        advanceUntilIdle()
        viewModel.selectTier(14)
        viewModel.requestMerge()
        assertNull(viewModel.uiState.value.merge)

        viewModel.selectTier(13)
        viewModel.requestMerge()
        advanceUntilIdle()

        val dialog = viewModel.uiState.value.merge!!
        assertEquals(13, dialog.tier.number)
        assertEquals("main", dialog.baseRef)
        assertEquals(LoadState.Loaded(listOf(MergeMethod.MERGE, MergeMethod.REBASE)), dialog.methods)
        assertEquals(MergeMethod.MERGE, dialog.method)
    }

    @Test
    fun `a merge sends the head commit and the chosen method, closes the dialog and reads the stack again`() = runTest {
        val viewModel = PullRequestViewModel(access, workspace)
        advanceUntilIdle()
        viewModel.requestMerge()
        advanceUntilIdle()

        viewModel.selectMergeMethod(MergeMethod.REBASE)
        viewModel.confirmMerge()
        val event = viewModel.eventFlow.first()
        advanceUntilIdle()

        assertEquals(MergeRequest(13, "sha-13", MergeMethod.REBASE), actions.merges.single())
        assertEquals(PullRequestEvent.ShowMessage("Merged #13 into main"), event)
        assertNull(viewModel.uiState.value.merge)
        assertEquals(2, github.lookups.size)
    }

    @Test
    fun `a refused merge keeps the dialog open with the GitHub message`() = runTest {
        actions.mergeFailure = ApiException.Client(405, null, "Pull Request is not mergeable")
        val viewModel = PullRequestViewModel(access, workspace)
        advanceUntilIdle()
        viewModel.requestMerge()
        advanceUntilIdle()

        viewModel.confirmMerge()
        advanceUntilIdle()

        val dialog = viewModel.uiState.value.merge!!
        assertEquals("Pull Request is not mergeable", dialog.error)
        assertEquals(false, dialog.isMerging)
        viewModel.dismissMerge()
        assertNull(viewModel.uiState.value.merge)
    }

    @Test
    fun `while a check runs and the bar is visible, the bar reads the stack every 30 seconds`() = runTest {
        actions.checks = listOf(check("build", CheckOutcome.PENDING))
        val viewModel = PullRequestViewModel(access, workspace)
        whileVisible(viewModel) {
            assertEquals(1, github.lookups.size)

            advanceAndRun(30_000)
            assertEquals(2, github.lookups.size)

            actions.checks = listOf(check("build", CheckOutcome.PASSED))
            advanceAndRun(30_000)
            assertEquals(3, github.lookups.size)
            advanceAndRun(60_000)
            assertEquals(3, github.lookups.size)
        }
    }

    @Test
    fun `a hidden bar stops reading`() = runTest {
        actions.checks = listOf(check("build", CheckOutcome.PENDING))
        val viewModel = PullRequestViewModel(access, workspace)
        viewModel.onVisible()
        runCurrent()

        viewModel.onHidden()
        advanceAndRun(60_000)

        assertEquals(1, github.lookups.size)
    }

    /** A failed assertion must not leave the poll running, or the end of runTest waits for it forever. */
    private fun TestScope.whileVisible(viewModel: PullRequestViewModel, block: () -> Unit) {
        viewModel.onVisible()
        runCurrent()
        try {
            block()
        } finally {
            viewModel.onHidden()
        }
    }

    private fun TestScope.advanceAndRun(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }
}

private class FakePullRequestActions : PullRequestActions {
    var checks = listOf(check("build", CheckOutcome.PASSED))
    var mergeFailure: ApiException? = null
    val checkRequests = mutableListOf<String>()
    val failureRequests = mutableListOf<List<Check>>()
    val merges = mutableListOf<MergeRequest>()

    override suspend fun checks(repository: RepositoryName, headSha: String): List<Check> {
        checkRequests += headSha
        return checks
    }

    override suspend fun checkFailures(repository: RepositoryName, failedChecks: List<Check>): List<CheckFailure> {
        failureRequests += failedChecks
        return failedChecks.map { CheckFailure(it, "error: ${it.name} failed") }
    }

    override suspend fun mergeMethods(repository: RepositoryName) = listOf(MergeMethod.MERGE, MergeMethod.REBASE)

    override suspend fun merge(repository: RepositoryName, request: MergeRequest) {
        mergeFailure?.let { throw it }
        merges += request
    }
}

private fun check(name: String, outcome: CheckOutcome) = Check(name, outcome, url = "https://github.com/example-org/example-repo/runs/1", summary = null, actionsJobId = 1)

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

    override suspend fun newestAppRelease(repository: RepositoryName): AppRelease? = null
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
    headSha = "sha-$number",
    mergeability = Mergeability.READY,
)
