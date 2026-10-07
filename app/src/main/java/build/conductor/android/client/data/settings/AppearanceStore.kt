package build.conductor.android.client.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The colour scheme that the user picked in Settings. */
enum class ColorSchemeChoice {
    /** The wallpaper colours on Android 12 and later, and a purple scheme before that. */
    DEFAULT,
    NEAPOLITAN,
    ;

    companion object {
        fun from(value: String?): ColorSchemeChoice = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

class AppearanceStore(private val dataStore: DataStore<Preferences>) {
    val colorScheme: Flow<ColorSchemeChoice> = dataStore.data.map { ColorSchemeChoice.from(it[COLOR_SCHEME]) }

    suspend fun saveColorScheme(choice: ColorSchemeChoice) {
        dataStore.edit { it[COLOR_SCHEME] = choice.name }
    }

    private companion object {
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
    }
}
