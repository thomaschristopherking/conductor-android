package build.conductor.android.client.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stores one secret in DataStore, encrypted with a key that only the Android Keystore holds. */
class SecretStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: AesGcmCipher,
    preferenceName: String,
) {
    private val preferenceKey = stringPreferencesKey(preferenceName)

    /** The saved secret, or null when no secret is saved or the saved secret cannot be decrypted. */
    val secret: Flow<String?> = dataStore.data.map { preferences ->
        preferences[preferenceKey]?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
    }

    suspend fun save(secret: String) {
        val encrypted = cipher.encrypt(secret.trim())
        dataStore.edit { it[preferenceKey] = encrypted }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(preferenceKey) }
    }

    companion object {
        const val API_KEY = "encrypted_api_key"
        const val GITHUB_TOKEN = "encrypted_github_token"
    }
}

/** A saved secret, after DataStore reads it. */
sealed interface SavedSecret {
    data object Loading : SavedSecret
    data object Missing : SavedSecret
    data class Present(val value: String) : SavedSecret
}

fun Flow<String?>.asSavedSecret(): Flow<SavedSecret> = map { if (it.isNullOrBlank()) SavedSecret.Missing else SavedSecret.Present(it) }

/** Shows only the last four characters of a key. */
fun maskApiKey(apiKey: String): String = "••••" + apiKey.takeLast(VISIBLE_KEY_CHARACTERS)

private const val VISIBLE_KEY_CHARACTERS = 4
