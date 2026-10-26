package build.conductor.android.client.data.github

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** The parts of the GitHub REST API that show and merge the pull requests of a workspace. */
interface GitHubApi {
    @GET("user")
    suspend fun user(@Header(AUTHORIZATION_HEADER) authorization: String? = null): GitHubUser

    /** The most recently updated pull requests first, open and closed. */
    @GET("repos/{owner}/{repo}/pulls")
    suspend fun pullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "all",
        @Query("sort") sort: String = "updated",
        @Query("direction") direction: String = "desc",
        @Query("per_page") perPage: Int = PAGE_SIZE,
    ): List<GitHubPullRequest>

    @GET("repos/{owner}/{repo}/pulls/{number}")
    suspend fun pullRequest(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int): GitHubPullRequest

    @GET("repos/{owner}/{repo}/pulls/{number}/files")
    suspend fun files(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = PAGE_SIZE,
    ): List<GitHubChangedFile>

    /** The stacks that hold the pull request. `gh stack` creates them. */
    @GET("repos/{owner}/{repo}/stacks")
    suspend fun stacks(@Path("owner") owner: String, @Path("repo") repo: String, @Query("pull_request") number: Int): List<GitHubStack>

    /** The most recently created releases first. */
    @GET("repos/{owner}/{repo}/releases")
    suspend fun releases(@Path("owner") owner: String, @Path("repo") repo: String, @Query("per_page") perPage: Int = RELEASE_PAGE_SIZE): List<GitHubRelease>

    /** The newest check run of each name; a check that runs again replaces the old one. */
    @GET("repos/{owner}/{repo}/commits/{sha}/check-runs")
    suspend fun checkRuns(@Path("owner") owner: String, @Path("repo") repo: String, @Path("sha") sha: String, @Query("per_page") perPage: Int = PAGE_SIZE): GitHubCheckRuns

    /** Commit statuses come from services that do not use check runs. */
    @GET("repos/{owner}/{repo}/commits/{sha}/status")
    suspend fun combinedStatus(@Path("owner") owner: String, @Path("repo") repo: String, @Path("sha") sha: String, @Query("per_page") perPage: Int = PAGE_SIZE): GitHubCombinedStatus

    /** The plain text log of a GitHub Actions job. GitHub redirects to a signed download, and OkHttp drops the token for the other host. */
    @Streaming
    @GET("repos/{owner}/{repo}/actions/jobs/{jobId}/logs")
    suspend fun jobLog(@Path("owner") owner: String, @Path("repo") repo: String, @Path("jobId") jobId: Long): ResponseBody

    @GET("repos/{owner}/{repo}")
    suspend fun mergeSettings(@Path("owner") owner: String, @Path("repo") repo: String): GitHubMergeSettings

    @PUT("repos/{owner}/{repo}/pulls/{number}/merge")
    suspend fun merge(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int, @Body request: GitHubMergeRequest): GitHubMergeResult

    companion object {
        const val RELEASE_PAGE_SIZE = 20

        const val AUTHORIZATION_HEADER = "Authorization"
        const val PAGE_SIZE = 100

        /** GitHub sends no more than 3000 files for one pull request. */
        const val MAX_FILE_PAGES = 30
    }
}
