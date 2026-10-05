package build.conductor.android.client.data.api

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

val conductorJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

val DEFAULT_BASE_URL: HttpUrl = "https://api.conductor.build/".toHttpUrl()

/** Builds the [ConductorApi]. [apiKey] is read for each request, so a new key takes effect at once. */
fun createConductorApi(
    apiKey: () -> String?,
    baseUrl: HttpUrl = DEFAULT_BASE_URL,
    retryDelay: (Long) -> Unit = Thread::sleep,
): ConductorApi = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(createHttpClient(apiKey, retryDelay))
    .addConverterFactory(conductorJson.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(ConductorApi::class.java)

private fun createHttpClient(apiKey: () -> String?, retryDelay: (Long) -> Unit): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // OkHttp would otherwise resend a POST after some connection failures, and creating a workspace is not idempotent.
        .retryOnConnectionFailure(false)
        .addInterceptor(AuthInterceptor(apiKey))
        .addInterceptor(RetryInterceptor(retryDelay))
        .build()

/** Adds the bearer token, unless the request already carries its own. */
class AuthInterceptor(private val apiKey: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = apiKey()
        if (request.header(ConductorApi.AUTHORIZATION_HEADER) != null || key.isNullOrBlank()) {
            return chain.proceed(request)
        }
        return chain.proceed(request.newBuilder().header(ConductorApi.AUTHORIZATION_HEADER, bearer(key)).build())
    }
}

fun bearer(apiKey: String): String = "Bearer ${apiKey.trim()}"

/**
 * Retries a GET request after a connection failure or HTTP 429, 502, 503 or 504.
 * Other methods are not idempotent, so they are never retried.
 */
class RetryInterceptor(
    private val delay: (Long) -> Unit,
    private val maxRetries: Int = MAX_RETRIES,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        var attempt = 0
        while (true) {
            val response = proceedOrNull(chain, isLastAttempt = attempt >= maxRetries)
            if (response != null && (response.code !in RETRYABLE_CODES || attempt >= maxRetries)) return response
            val waitMillis = response?.let(::retryAfterMillis) ?: (BASE_RETRY_MILLIS shl attempt)
            response?.close()
            delay(waitMillis.coerceAtMost(MAX_RETRY_MILLIS))
            attempt++
        }
    }

    /** Returns null after a connection failure that can be retried. */
    private fun proceedOrNull(chain: Interceptor.Chain, isLastAttempt: Boolean): Response? = try {
        chain.proceed(chain.request())
    } catch (exception: IOException) {
        if (isLastAttempt || chain.call().isCanceled()) throw exception
        null
    }

    private fun retryAfterMillis(response: Response): Long? =
        response.header("Retry-After")?.trim()?.toLongOrNull()?.let { TimeUnit.SECONDS.toMillis(it) }

    private companion object {
        val RETRYABLE_CODES = setOf(429, 502, 503, 504)
        const val MAX_RETRIES = 2
        const val BASE_RETRY_MILLIS = 1_000L
        const val MAX_RETRY_MILLIS = 10_000L
    }
}

private const val TIMEOUT_SECONDS = 30L
