package build.conductor.android.client.data.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(val login: String, val name: String? = null)

@Serializable
data class GitHubBranch(val ref: String, val sha: String = "", val repo: GitHubRepositoryRef? = null)

@Serializable
data class GitHubRepositoryRef(@SerialName("full_name") val fullName: String)

/** The list endpoint leaves out [additions], [deletions], [changedFiles], [mergeable] and [mergeableState]. */
@Serializable
data class GitHubPullRequest(
    val number: Int,
    val title: String,
    val state: String,
    val head: GitHubBranch,
    val base: GitHubBranch,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    @SerialName("merged_at") val mergedAt: String? = null,
    val body: String? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
    @SerialName("changed_files") val changedFiles: Int? = null,
    /** Null while GitHub computes it. */
    val mergeable: Boolean? = null,
    @SerialName("mergeable_state") val mergeableState: String? = null,
)

@Serializable
data class GitHubChangedFile(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val patch: String? = null,
    @SerialName("previous_filename") val previousFilename: String? = null,
)

/** The pull requests of a stack, from the bottom of the stack to the top. */
@Serializable
data class GitHubStack(
    val number: Int,
    val base: GitHubBranch,
    @SerialName("pull_requests") val pullRequests: List<GitHubStackEntry>,
)

@Serializable
data class GitHubStackEntry(val number: Int)

/** The list endpoint sends drafts too when the token can push to the repository. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

@Serializable
data class GitHubCheckRuns(@SerialName("check_runs") val checkRuns: List<GitHubCheckRun>)

/** [conclusion] is null until [status] is `completed`. */
@Serializable
data class GitHubCheckRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val output: GitHubCheckOutput? = null,
    val app: GitHubApp? = null,
)

@Serializable
data class GitHubCheckOutput(val title: String? = null, val summary: String? = null)

@Serializable
data class GitHubApp(val slug: String? = null)

/** The `state` of the combined status is `pending` when the commit has no statuses, so the app reads [statuses] only. */
@Serializable
data class GitHubCombinedStatus(val statuses: List<GitHubCommitStatus>)

@Serializable
data class GitHubCommitStatus(
    val context: String,
    val state: String,
    val description: String? = null,
    @SerialName("target_url") val targetUrl: String? = null,
)

@Serializable
data class GitHubMergeSettings(
    @SerialName("allow_squash_merge") val allowSquashMerge: Boolean = true,
    @SerialName("allow_merge_commit") val allowMergeCommit: Boolean = true,
    @SerialName("allow_rebase_merge") val allowRebaseMerge: Boolean = true,
)

/** [sha] makes GitHub refuse the merge with 409 when the branch has a newer commit than the one that the user saw. */
@Serializable
data class GitHubMergeRequest(@SerialName("merge_method") val mergeMethod: String, val sha: String)

@Serializable
data class GitHubMergeResult(val merged: Boolean, val message: String? = null)

@Serializable
data class GitHubError(val message: String? = null)
