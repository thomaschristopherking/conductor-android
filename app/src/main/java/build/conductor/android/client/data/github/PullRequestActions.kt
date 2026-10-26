package build.conductor.android.client.data.github

import build.conductor.android.client.data.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** The GitHub merge methods, in the order that the app offers them. */
enum class MergeMethod(val apiName: String, val label: String) {
    SQUASH("squash", "Squash and merge"),
    MERGE("merge", "Create a merge commit"),
    REBASE("rebase", "Rebase and merge"),
}

data class MergeRequest(val number: Int, val headSha: String, val method: MergeMethod)

/** The checks of a pull request, and its merge. Every call throws [ApiException] on failure. */
interface PullRequestActions {
    suspend fun checks(repository: RepositoryName, headSha: String): List<Check>

    /** The end of the log of the first failed GitHub Actions jobs. A log that the app cannot read gives a failure without an excerpt. */
    suspend fun checkFailures(repository: RepositoryName, failedChecks: List<Check>): List<CheckFailure>

    /** The merge methods that the repository allows. */
    suspend fun mergeMethods(repository: RepositoryName): List<MergeMethod>

    suspend fun merge(repository: RepositoryName, request: MergeRequest)
}

class ApiPullRequestActions(private val api: GitHubApi) : PullRequestActions {
    override suspend fun checks(repository: RepositoryName, headSha: String): List<Check> = withAccessMessage(CHECKS_ACCESS) {
        coroutineScope {
            val checkRuns = async { api.checkRuns(repository.owner, repository.name, headSha).checkRuns.map { it.toCheck() } }
            val statuses = async { api.combinedStatus(repository.owner, repository.name, headSha).statuses.map { it.toCheck() } }
            checkRuns.await() + statuses.await()
        }
    }

    override suspend fun checkFailures(repository: RepositoryName, failedChecks: List<Check>): List<CheckFailure> = coroutineScope {
        val loggedJobIds = failedChecks.mapNotNull { it.actionsJobId }.take(MAX_LOGS).toSet()
        failedChecks.map { check ->
            async { CheckFailure(check, check.actionsJobId?.takeIf { it in loggedJobIds }?.let { logExcerptOrNull(repository, it) }) }
        }.awaitAll()
    }

    private suspend fun logExcerptOrNull(repository: RepositoryName, jobId: Long): String? = try {
        mapGitHubErrors {
            val log = api.jobLog(repository.owner, repository.name, jobId)
            withContext(Dispatchers.IO) { log.charStream().buffered().useLines(::failureExcerpt) }
        }
    } catch (_: ApiException) {
        null
    }

    override suspend fun mergeMethods(repository: RepositoryName): List<MergeMethod> = mapGitHubErrors {
        val settings = api.mergeSettings(repository.owner, repository.name)
        MergeMethod.entries.filter { method -> settings.allows(method) }
    }

    private fun GitHubMergeSettings.allows(method: MergeMethod): Boolean = when (method) {
        MergeMethod.SQUASH -> allowSquashMerge
        MergeMethod.MERGE -> allowMergeCommit
        MergeMethod.REBASE -> allowRebaseMerge
    }

    override suspend fun merge(repository: RepositoryName, request: MergeRequest) {
        val result = withAccessMessage(MERGE_ACCESS) {
            api.merge(repository.owner, repository.name, request.number, GitHubMergeRequest(request.method.apiName, request.headSha))
        }
        if (!result.merged) throw ApiException.Client(HTTP_OK, null, result.message ?: "GitHub did not merge the pull request.")
    }

    /** GitHub answers 403 or 404 when the token lacks a permission; the message names the permission that this call needs. */
    private suspend fun <T> withAccessMessage(access: String, block: suspend () -> T): T = try {
        mapGitHubErrors(block)
    } catch (exception: ApiException.Client) {
        if (exception.statusCode != HTTP_FORBIDDEN && exception.statusCode != HTTP_NOT_FOUND) throw exception
        throw ApiException.Client(exception.statusCode, exception.code, "GitHub did not give access (HTTP ${exception.statusCode}). $access")
    }

    private companion object {
        /** Five logs keep the prompt short enough to read. */
        const val MAX_LOGS = 5
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val CHECKS_ACCESS = "The token must have read access to the checks and commit statuses of this repository."
        const val MERGE_ACCESS = "The token must have write access to the contents of this repository."
    }
}
