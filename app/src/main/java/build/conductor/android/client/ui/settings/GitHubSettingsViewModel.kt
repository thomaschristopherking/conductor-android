package build.conductor.android.client.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.github.GitHubRepository
import build.conductor.android.client.data.settings.SavedSecret
import build.conductor.android.client.data.settings.SecretStore
import build.conductor.android.client.data.settings.maskApiKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GitHubSettingsUiState(
    val tokenInput: String = "",
    val savedTokenMask: String? = null,
    val isTesting: Boolean = false,
    val signedInAs: String? = null,
    val error: String? = null,
)

/** The optional GitHub token, which shows the pull request, the stack and the changes of a workspace. */
class GitHubSettingsViewModel(
    private val github: GitHubRepository,
    private val tokenStore: SecretStore,
    savedToken: StateFlow<SavedSecret>,
) : ViewModel() {
    private val form = MutableStateFlow(GitHubSettingsUiState())

    val uiState: StateFlow<GitHubSettingsUiState> = combine(form, savedToken) { state, token ->
        state.copy(savedTokenMask = (token as? SavedSecret.Present)?.value?.let(::maskApiKey))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), GitHubSettingsUiState())

    fun onTokenInputChange(value: String) = form.update { it.copy(tokenInput = value, error = null) }

    fun testAndSave() {
        val candidate = form.value.tokenInput.trim()
        if (candidate.isEmpty() || form.value.isTesting) return
        form.update { it.copy(isTesting = true, error = null, signedInAs = null) }
        viewModelScope.launch { verifyAndSave(candidate) }
    }

    private suspend fun verifyAndSave(candidate: String) {
        try {
            val user = github.testToken(candidate)
            tokenStore.save(candidate)
            form.update { it.copy(isTesting = false, tokenInput = "", signedInAs = user.login) }
        } catch (exception: ApiException) {
            form.update { it.copy(isTesting = false, error = exception.message) }
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            tokenStore.clear()
            form.update { GitHubSettingsUiState() }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
