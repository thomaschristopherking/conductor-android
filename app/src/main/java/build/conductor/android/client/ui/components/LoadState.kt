package build.conductor.android.client.ui.components

import build.conductor.android.client.data.api.ApiException

/** The state of a screen's main content. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
    data class Failed(val message: String, val isRetryable: Boolean) : LoadState<Nothing>
}

fun ApiException.toFailedState(): LoadState.Failed = LoadState.Failed(message.orEmpty(), isRetryable || this is ApiException.Client)
