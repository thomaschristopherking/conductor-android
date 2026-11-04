package build.conductor.android.client.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import build.conductor.android.client.voice.VoiceFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VoiceSettingsStore(private val dataStore: DataStore<Preferences>) {
    val voiceFilter: Flow<VoiceFilter> = dataStore.data.map { VoiceFilter.from(it[VOICE_FILTER]) }

    suspend fun saveVoiceFilter(filter: VoiceFilter) {
        dataStore.edit { it[VOICE_FILTER] = filter.name }
    }

    private companion object {
        val VOICE_FILTER = stringPreferencesKey("voice_filter")
    }
}
