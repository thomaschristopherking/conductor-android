package build.conductor.android.client.data.github

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/** The parts of the GitHub REST API that show the pull requests of a workspace. */
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

    companion object {
        const val RELEASE_PAGE_SIZE = 20

        const val AUTHORIZATION_HEADER = "Authorization"
        const val PAGE_SIZE = 100

        /** GitHub sends no more than 3000 files for one pull request. */
        const val MAX_FILE_PAGES = 30
    }
}
