@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.data.github

import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import build.conductor.android.client.data.api.ApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackPositionsTest {
    private val github = PositionsGitHubRepository()
    private val isConnected = MutableStateFlow(true)
    private val pushWorkspace = workspace("w1", "push").copy(number = 772)
    private val otherRepositoryWorkspace = workspace("w2", "docs").copy(repoUrl = "https://github.com/example-org/other-repo")

    @Test
    fun `each repository loads once, and the positions are by workspace id`() = runTest {
        val positions = startPositions()

        positions.load(listOf(pushWorkspace, otherRepositoryWorkspace, workspace("w3", "menu")))
        runCurrent()

        assertEquals(mapOf("w1" to StackPosition(1, 2)), positions.positions.value)
        assertEquals(listOf("example-repo", "other-repo"), github.requests.map { it.first.name }.sorted())
        assertEquals(listOf(WorkspaceKey(772, "push"), WorkspaceKey(null, "menu")), github.requests.first { it.first.name == "example-repo" }.second)
    }

    @Test
    fun `without a token nothing loads, and a saved token loads the latest list`() = runTest {
        isConnected.value = false
        val positions = startPositions()

        positions.load(listOf(pushWorkspace))
        runCurrent()
        assertTrue(github.requests.isEmpty())

        isConnected.value = true
        runCurrent()

        assertEquals(mapOf("w1" to StackPosition(1, 2)), positions.positions.value)
    }

    @Test
    fun `a removed token clears the positions`() = runTest {
        val positions = startPositions()
        positions.load(listOf(pushWorkspace))
        runCurrent()

        isConnected.value = false
        runCurrent()

        assertEquals(emptyMap<String, StackPosition>(), positions.positions.value)
    }

    @Test
    fun `a failure clears the positions of its repository only, and a new load replaces the earlier positions`() = runTest {
        val positions = startPositions()
        github.positions = mapOf(WorkspaceKey(772, "push") to StackPosition(1, 2), WorkspaceKey(null, "docs") to StackPosition(3, 3))
        positions.load(listOf(pushWorkspace, otherRepositoryWorkspace))
        runCurrent()

        github.failingRepository = "other-repo"
        github.positions = mapOf(WorkspaceKey(772, "push") to StackPosition(2, 2))
        positions.load(listOf(pushWorkspace, otherRepositoryWorkspace))
        runCurrent()

        assertEquals(mapOf("w1" to StackPosition(2, 2)), positions.positions.value)
    }

    private fun TestScope.startPositions() = GitHubStackPositions(github, isConnected, backgroundScope).also { runCurrent() }
}

private class PositionsGitHubRepository : GitHubRepository {
    var positions = mapOf(WorkspaceKey(772, "push") to StackPosition(1, 2))
    var failingRepository: String? = null
    val requests = mutableListOf<Pair<RepositoryName, List<WorkspaceKey>>>()

    override suspend fun testToken(token: String) = GitHubUser("octocat")

    override suspend fun workspaceStack(repository: RepositoryName, workspace: WorkspaceKey): PullRequestStack? = null

    override suspend fun stackPositions(repository: RepositoryName, workspaces: List<WorkspaceKey>): Map<WorkspaceKey, StackPosition> {
        requests += repository to workspaces
        if (repository.name == failingRepository) throw ApiException.Server(502, "GitHub is not available (HTTP 502). Try again.")
        return positions.filterKeys { it in workspaces }
    }

    override suspend fun changedFiles(repository: RepositoryName, number: Int): List<FileChange> = emptyList()

    override suspend fun newestAppRelease(repository: RepositoryName): AppRelease? = null
}
