package build.conductor.android.client.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.settings.VoiceSettingsStore
import build.conductor.android.client.voice.VoiceFilter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoiceSettingsViewModel(private val store: VoiceSettingsStore) : ViewModel() {
    val voiceFilter: StateFlow<VoiceFilter> = store.voiceFilter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), VoiceFilter.DEFAULT)

    fun selectVoiceFilter(filter: VoiceFilter) {
        viewModelScope.launch { store.saveVoiceFilter(filter) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
