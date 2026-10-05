package build.conductor.android.client.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.ApiKeyState
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.settings.ApiKeyStore
import build.conductor.android.client.data.settings.maskApiKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val keyInput: String = "",
    val savedKeyMask: String? = null,
    val isTesting: Boolean = false,
    val signedInAs: String? = null,
    val error: String? = null,
)

sealed interface SettingsEvent {
    data object KeySaved : SettingsEvent
}

class SettingsViewModel(
    private val repository: ConductorRepository,
    private val apiKeyStore: ApiKeyStore,
    apiKeyState: StateFlow<ApiKeyState>,
) : ViewModel() {
    private val form = MutableStateFlow(SettingsUiState())
    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<SettingsEvent> = events.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(form, apiKeyState) { state, keyState ->
        state.copy(savedKeyMask = (keyState as? ApiKeyState.Present)?.apiKey?.let(::maskApiKey))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun onKeyInputChange(value: String) {
        form.update { it.copy(keyInput = value, error = null) }
    }

    fun testAndSave() {
        val candidate = form.value.keyInput.trim()
        if (candidate.isEmpty() || form.value.isTesting) return
        form.update { it.copy(isTesting = true, error = null, signedInAs = null) }
        viewModelScope.launch { verifyAndSave(candidate) }
    }

    private suspend fun verifyAndSave(candidate: String) {
        try {
            val me = repository.testApiKey(candidate)
            apiKeyStore.save(candidate)
            form.update { it.copy(isTesting = false, keyInput = "", signedInAs = me.name ?: me.email ?: me.userId) }
            events.send(SettingsEvent.KeySaved)
        } catch (exception: ApiException) {
            form.update { it.copy(isTesting = false, error = exception.toSettingsMessage()) }
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            apiKeyStore.clear()
            form.update { SettingsUiState() }
        }
    }

    private fun ApiException.toSettingsMessage(): String =
        if (this is ApiException.Unauthorized) "Conductor rejected this key. Check it and try again." else message.orEmpty()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
