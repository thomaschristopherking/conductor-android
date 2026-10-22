package build.conductor.android.client.data.github

import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.AuthInterceptor
import build.conductor.android.client.data.api.RetryInterceptor
import build.conductor.android.client.data.api.conductorJson
import build.conductor.android.client.data.api.mapApiErrors
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val GITHUB_BASE_URL: HttpUrl = "https://api.github.com/".toHttpUrl()

/** Builds the [GitHubApi]. [token] is read for each request, so a new token takes effect at once. */
fun createGitHubApi(token: () -> String?, baseUrl: HttpUrl = GITHUB_BASE_URL, retryDelay: (Long) -> Unit = Thread::sleep): GitHubApi =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(AuthInterceptor(token))
                .addInterceptor(GitHubHeadersInterceptor())
                .addInterceptor(RetryInterceptor(retryDelay))
                .build(),
        )
        .addConverterFactory(conductorJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GitHubApi::class.java)

private class GitHubHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .build(),
    )
}

/** Runs [block] and converts every failure into an [ApiException] with a message about GitHub, not Conductor. */
suspend fun <T> mapGitHubErrors(block: suspend () -> T): T = mapApiErrors {
    try {
        block()
    } catch (exception: HttpException) {
        throw exception.toGitHubException()
    }
}

private fun HttpException.toGitHubException(): ApiException {
    val response = response()
    val detail = response?.errorBody()?.string()?.let { runCatching { conductorJson.decodeFromString(GitHubError.serializer(), it).message }.getOrNull() }
    val isRateLimited = response?.headers()?.get("x-ratelimit-remaining") == "0"
    return when {
        code() == HTTP_UNAUTHORIZED -> ApiException.Unauthorized("GitHub rejected the token. Paste a new token in Settings.")
        code() == HTTP_TOO_MANY_REQUESTS || (code() == HTTP_FORBIDDEN && isRateLimited) ->
            ApiException.RateLimited("GitHub limits the requests of this token. Try again later.")
        code() == HTTP_FORBIDDEN || code() == HTTP_NOT_FOUND ->
            ApiException.Client(code(), null, "GitHub did not give access (HTTP ${code()}). The token must have read access to the pull requests of this repository.")
        code() >= HTTP_SERVER_ERROR -> ApiException.Server(code(), "GitHub is not available (HTTP ${code()}). Try again.")
        else -> ApiException.Client(code(), null, detail ?: "The GitHub request failed (HTTP ${code()}).")
    }
}

private const val API_VERSION = "2022-11-28"
private const val TIMEOUT_SECONDS = 30L
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500
