package build.conductor.android.client.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stores the API key in DataStore, encrypted with a key that only the Android Keystore holds. */
class ApiKeyStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: AesGcmCipher,
) {
    /** The saved key, or null when no key is saved or the saved key cannot be decrypted. */
    val apiKey: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ENCRYPTED_API_KEY]?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
    }

    suspend fun save(apiKey: String) {
        val encrypted = cipher.encrypt(apiKey.trim())
        dataStore.edit { it[ENCRYPTED_API_KEY] = encrypted }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(ENCRYPTED_API_KEY) }
    }

    private companion object {
        val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
    }
}

/** Shows only the last four characters of a key. */
fun maskApiKey(apiKey: String): String = "••••" + apiKey.takeLast(VISIBLE_KEY_CHARACTERS)

private const val VISIBLE_KEY_CHARACTERS = 4
