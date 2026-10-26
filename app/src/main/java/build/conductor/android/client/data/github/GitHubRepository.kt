package build.conductor.android.client.data.github

import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.bearer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Every call throws [ApiException] on failure. */
interface GitHubRepository {
    suspend fun testToken(token: String): GitHubUser

    /** The stack that holds the pull request of the workspace, or null when the workspace has no pull request. */
    suspend fun workspaceStack(repository: RepositoryName, workspace: WorkspaceKey): PullRequestStack?

    /** The place of each workspace whose pull request is in a stack of two or more. Other workspaces are not in the map. */
    suspend fun stackPositions(repository: RepositoryName, workspaces: List<WorkspaceKey>): Map<WorkspaceKey, StackPosition>

    suspend fun changedFiles(repository: RepositoryName, number: Int): List<FileChange>
}

class ApiGitHubRepository(private val api: GitHubApi) : GitHubRepository {
    private val detailPermits = Semaphore(MAX_PARALLEL_DETAIL_CALLS)

    override suspend fun testToken(token: String): GitHubUser = mapGitHubErrors { api.user(bearer(token)) }

    override suspend fun workspaceStack(repository: RepositoryName, workspace: WorkspaceKey): PullRequestStack? = mapGitHubErrors {
        val candidates = api.pullRequests(repository.owner, repository.name)
        findWorkspacePullRequest(candidates, workspace)?.let { pullRequest -> stackOf(repository, pullRequest, candidates) }
    }

    private suspend fun stackOf(repository: RepositoryName, pullRequest: GitHubPullRequest, candidates: List<GitHubPullRequest>): PullRequestStack {
        val gitHubStack = gitHubStack(repository, pullRequest.number)
        val tiers = summaries(repository, gitHubStack?.topDownNumbers() ?: chainedStackNumbers(pullRequest, candidates))
        return PullRequestStack(tiers, gitHubStack?.base?.ref ?: tiers.last().baseRef, pullRequest.number)
    }

    override suspend fun stackPositions(repository: RepositoryName, workspaces: List<WorkspaceKey>): Map<WorkspaceKey, StackPosition> = mapGitHubErrors {
        val candidates = api.pullRequests(repository.owner, repository.name)
        val pullRequests = workspaces.mapNotNull { workspace -> findWorkspacePullRequest(candidates, workspace)?.let { workspace to it } }
        coroutineScope {
            pullRequests.map { (workspace, pullRequest) -> async { workspace to positionOf(repository, pullRequest, candidates) } }.awaitAll()
        }.mapNotNull { (workspace, position) -> position?.let { workspace to it } }.toMap()
    }

    private suspend fun positionOf(repository: RepositoryName, pullRequest: GitHubPullRequest, candidates: List<GitHubPullRequest>): StackPosition? {
        val gitHubStack = detailPermits.withPermit { gitHubStack(repository, pullRequest.number) }
        return stackPosition(gitHubStack?.topDownNumbers() ?: chainedStackNumbers(pullRequest, candidates), pullRequest.number)
    }

    /** A repository without stacks answers 404. */
    private suspend fun gitHubStack(repository: RepositoryName, number: Int): GitHubStack? = try {
        mapGitHubErrors { api.stacks(repository.owner, repository.name, number) }.firstOrNull { stack -> stack.pullRequests.any { it.number == number } }
    } catch (exception: ApiException.Client) {
        if (exception.statusCode == HTTP_NOT_FOUND) null else throw exception
    }

    private fun GitHubStack.topDownNumbers(): List<Int> = pullRequests.map { it.number }.reversed()

    /** Only the endpoint for one pull request sends its additions and deletions. */
    private suspend fun summaries(repository: RepositoryName, numbers: List<Int>): List<PullRequestSummary> = coroutineScope {
        numbers.map { number ->
            async { detailPermits.withPermit { api.pullRequest(repository.owner, repository.name, number).toSummary() } }
        }.awaitAll()
    }

    override suspend fun changedFiles(repository: RepositoryName, number: Int): List<FileChange> = mapGitHubErrors {
        val files = mutableListOf<GitHubChangedFile>()
        for (page in 1..GitHubApi.MAX_FILE_PAGES) {
            val batch = api.files(repository.owner, repository.name, number, page)
            files += batch
            if (batch.size < GitHubApi.PAGE_SIZE) break
        }
        files.map { it.toFileChange() }
    }

    private companion object {
        const val MAX_PARALLEL_DETAIL_CALLS = 6
        const val HTTP_NOT_FOUND = 404
    }
}
