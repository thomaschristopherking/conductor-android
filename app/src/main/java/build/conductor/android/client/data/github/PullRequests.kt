package build.conductor.android.client.data.github

/** A repository on github.com, from the repository URL of a workspace. */
data class RepositoryName(val owner: String, val name: String) {
    companion object {
        private val GITHUB_URL = Regex("""^(?:https?://|git@)github\.com[/:]([^/]+)/([^/]+?)(?:\.git)?/?$""")

        /** Null when the repository is not on github.com. */
        fun from(repoUrl: String): RepositoryName? =
            GITHUB_URL.find(repoUrl.trim())?.destructured?.let { (owner, name) -> RepositoryName(owner, name) }
    }
}

/** What the app knows of a workspace to find its pull request. [number] is the number in its Conductor web link. */
data class WorkspaceKey(val number: Int?, val name: String)

enum class PullRequestState {
    OPEN,
    DRAFT,
    MERGED,
    CLOSED,
    ;

    companion object {
        fun of(pullRequest: GitHubPullRequest): PullRequestState = when {
            pullRequest.mergedAt != null -> MERGED
            pullRequest.state != OPEN_STATE -> CLOSED
            pullRequest.draft -> DRAFT
            else -> OPEN
        }
    }
}

data class DiffCount(val additions: Int, val deletions: Int) {
    operator fun plus(other: DiffCount) = DiffCount(additions + other.additions, deletions + other.deletions)

    companion object {
        val ZERO = DiffCount(0, 0)
    }
}

data class PullRequestSummary(
    val number: Int,
    val title: String,
    val state: PullRequestState,
    val headRef: String,
    val baseRef: String,
    val url: String,
    val diff: DiffCount,
    val changedFiles: Int,
)

/** The pull requests of a stack. [tiers] start at the top; the last tier merges into [baseRef]. A lone pull request is a stack of one. */
data class PullRequestStack(val tiers: List<PullRequestSummary>, val baseRef: String, val workspaceNumber: Int) {
    val workspaceTier: PullRequestSummary
        get() = tiers.first { it.number == workspaceNumber }
}

enum class ChangeKind {
    ADDED,
    MODIFIED,
    RENAMED,
    REMOVED,
    ;

    companion object {
        /** GitHub sends `added`, `removed`, `modified`, `renamed`, `copied`, `changed` or `unchanged`. */
        fun from(status: String): ChangeKind = when (status) {
            "added", "copied" -> ADDED
            "removed" -> REMOVED
            "renamed" -> RENAMED
            else -> MODIFIED
        }
    }
}

/** One changed file. [patch] is null for a binary file and for a very large diff. */
data class FileChange(
    val path: String,
    val kind: ChangeKind,
    val diff: DiffCount,
    val patch: String?,
    val previousPath: String?,
)

/**
 * The pull request of a workspace. Conductor puts the web link of the workspace, `conductor.build/.../w/{number}`, in the body of a
 * pull request that an agent creates. A pull request whose branch ends with the workspace name is the second choice. Open pull requests
 * come first; [candidates] are the most recently updated first.
 */
fun findWorkspacePullRequest(candidates: List<GitHubPullRequest>, workspace: WorkspaceKey): GitHubPullRequest? {
    val linked = workspace.number?.let { number -> candidates.filter { it.body.orEmpty().contains(workspaceLink(number)) } }.orEmpty()
    val branchSuffix = branchName(workspace.name)
    val named = candidates.filter { it.head.ref == branchSuffix || it.head.ref.endsWith("/$branchSuffix") }
    return preferOpen(linked) ?: preferOpen(named)
}

private fun workspaceLink(number: Int) = Regex("""conductor\.build/\S*/w/$number(?!\d)""")

private fun preferOpen(pullRequests: List<GitHubPullRequest>): GitHubPullRequest? =
    pullRequests.firstOrNull { it.state == OPEN_STATE } ?: pullRequests.firstOrNull()

/** Conductor names a branch after the workspace, in lower case with dashes. */
private fun branchName(workspaceName: String): String = workspaceName.trim().lowercase().replace(Regex("""[^a-z0-9._/-]+"""), "-")

/**
 * The numbers of a stack that has no stack on GitHub, from the top down: each open pull request whose base is the branch of the one
 * below it. A pull request that is not open is a stack of one.
 */
fun chainedStackNumbers(pullRequest: GitHubPullRequest, candidates: List<GitHubPullRequest>): List<Int> {
    if (pullRequest.state != OPEN_STATE) return listOf(pullRequest.number)
    val open = candidates.filter { it.state == OPEN_STATE && it.number != pullRequest.number }
    val below = generateSequence(pullRequest) { tier -> open.firstOrNull { it.head.ref == tier.base.ref } }.drop(1).take(open.size).toList()
    val above = generateSequence(pullRequest) { tier -> open.firstOrNull { it.base.ref == tier.head.ref } }.drop(1).take(open.size).toList()
    return (above.reversed() + pullRequest + below).map { it.number }.distinct()
}

fun GitHubPullRequest.toSummary() = PullRequestSummary(
    number = number,
    title = title,
    state = PullRequestState.of(this),
    headRef = head.ref,
    baseRef = base.ref,
    url = htmlUrl,
    diff = DiffCount(additions ?: 0, deletions ?: 0),
    changedFiles = changedFiles ?: 0,
)

fun GitHubChangedFile.toFileChange() = FileChange(filename, ChangeKind.from(status), DiffCount(additions, deletions), patch, previousFilename)

private const val OPEN_STATE = "open"
