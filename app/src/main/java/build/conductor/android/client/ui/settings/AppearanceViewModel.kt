package build.conductor.android.client.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import build.conductor.android.client.data.settings.AppearanceStore
import build.conductor.android.client.data.settings.ColorSchemeChoice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppearanceViewModel(private val appearanceStore: AppearanceStore) : ViewModel() {
    val colorScheme: StateFlow<ColorSchemeChoice> = appearanceStore.colorScheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ColorSchemeChoice.DEFAULT)

    fun selectColorScheme(choice: ColorSchemeChoice) {
        viewModelScope.launch { appearanceStore.saveColorScheme(choice) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
