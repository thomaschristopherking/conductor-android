package build.conductor.android.client.data.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(val login: String, val name: String? = null)

@Serializable
data class GitHubBranch(val ref: String, val repo: GitHubRepositoryRef? = null)

@Serializable
data class GitHubRepositoryRef(@SerialName("full_name") val fullName: String)

/** The list endpoint leaves out [additions], [deletions] and [changedFiles]. */
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

@Serializable
data class GitHubError(val message: String? = null)
