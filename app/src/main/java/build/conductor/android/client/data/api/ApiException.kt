package build.conductor.android.client.data.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** A failed API call, grouped by what the user can do about it. */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The key is missing, wrong or revoked. The user must enter a new key. */
    class Unauthorized(message: String) : ApiException(message)

    /** The device has no connection, or the connection dropped. */
    class Offline(cause: IOException) : ApiException("You are offline. Check the connection and try again.", cause)

    /** The server rejected the call because of too many requests. */
    class RateLimited(message: String) : ApiException(message)

    /** The server failed. The same call can succeed later. */
    class Server(val statusCode: Int, message: String) : ApiException(message)

    /** The server rejected the request, for example 400 or 404. */
    class Client(val statusCode: Int, val code: String?, message: String) : ApiException(message)

    /** The response did not have the expected shape. */
    class UnexpectedResponse(cause: Throwable) : ApiException("The server sent a response that the app cannot read.", cause)

    val isRetryable: Boolean
        get() = this is Offline || this is RateLimited || this is Server
}

private val errorJson = Json { ignoreUnknownKeys = true }

/** Runs [block] and converts every transport or HTTP failure into an [ApiException]. */
suspend fun <T> mapApiErrors(block: suspend () -> T): T = try {
    block()
} catch (exception: CancellationException) {
    throw exception
} catch (exception: ApiException) {
    throw exception
} catch (exception: HttpException) {
    throw exception.toApiException()
} catch (exception: IOException) {
    throw ApiException.Offline(exception)
} catch (exception: SerializationException) {
    throw ApiException.UnexpectedResponse(exception)
} catch (exception: IllegalArgumentException) {
    throw ApiException.UnexpectedResponse(exception)
}

internal fun HttpException.toApiException(): ApiException {
    val body = parseErrorBody(response()?.errorBody()?.string())
    val message = body?.userMessage ?: defaultMessage(code())
    return when {
        code() == HTTP_UNAUTHORIZED -> ApiException.Unauthorized(message)
        code() == HTTP_TOO_MANY_REQUESTS -> ApiException.RateLimited(message)
        code() >= HTTP_SERVER_ERROR -> ApiException.Server(code(), message)
        else -> ApiException.Client(code(), body?.code, message)
    }
}

private fun parseErrorBody(text: String?): StructuredError? = text?.let {
    runCatching { errorJson.decodeFromString(StructuredError.serializer(), it) }.getOrNull()
}

private fun defaultMessage(statusCode: Int): String = when {
    statusCode == HTTP_UNAUTHORIZED -> "The API key is not valid."
    statusCode == HTTP_TOO_MANY_REQUESTS -> "Too many requests. Wait a moment and try again."
    statusCode >= HTTP_SERVER_ERROR -> "Conductor is not available (HTTP $statusCode). Try again."
    else -> "The request failed (HTTP $statusCode)."
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500
