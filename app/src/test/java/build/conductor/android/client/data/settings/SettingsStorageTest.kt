package build.conductor.android.client.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import build.conductor.android.client.data.api.SessionStatus
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

class SettingsStorageTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val softwareKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmCipher { softwareKey }

    @Test
    fun `the cipher round-trips text and uses a new IV each time`() {
        val first = cipher.encrypt("secret-key")
        val second = cipher.encrypt("secret-key")

        assertEquals("secret-key", cipher.decrypt(first))
        assertFalse(first == second)
        assertFalse(first.contains("secret-key"))
    }

    @Test
    fun `the key store saves an encrypted key, reads it back and clears it`() = runTest {
        val dataStoreFile = folder.newFile("settings.preferences_pb").also { it.delete() }
        val store = ApiKeyStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { dataStoreFile }, cipher)

        store.save("  my-key  ")
        assertEquals("my-key", store.apiKey.first())
        assertFalse(dataStoreFile.readBytes().decodeToString().contains("my-key"))
        store.clear()
        assertNull(store.apiKey.first())
    }

    @Test
    fun `a key that the cipher cannot decrypt reads as no key`() = runTest {
        val dataStore = newDataStore("broken")
        ApiKeyStore(dataStore, cipher).save("my-key")
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        assertNull(ApiKeyStore(dataStore, AesGcmCipher { otherKey }).apiKey.first())
    }

    @Test
    fun `workspace subscriptions are added, record what the screen saw, and are removed`() = runTest {
        val store = WorkspaceSubscriptionStore(newDataStore("subscriptions"))

        store.subscribe("w1", "Fix CI", mapOf("s1" to "working"))
        store.recordStatus(SessionStatus("w1", "s2", "idle", "2026-10-05T07:00:00Z"))
        store.recordStatus(SessionStatus("w9", "s3", "idle", "2026-10-05T07:00:00Z"))
        store.recordSeenQuestion("s1", "t1")
        store.recordSeenQuestion("s3", "t2")

        val expected = WorkspaceSubscription(
            "Fix CI",
            sessions = mapOf("s1" to WatchedSession(status = "working", seenQuestionId = "t1"), "s2" to WatchedSession("idle", "2026-10-05T07:00:00Z")),
        )
        assertEquals(mapOf("w1" to expected), store.snapshot())
        assertEquals(setOf("w1"), store.subscribedIds.first())
        store.unsubscribe("w1")
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun `each colour scheme is saved and read back, and an unknown value reads as the default`() = runTest {
        val dataStore = newDataStore("appearance")
        val store = AppearanceStore(dataStore)
        assertEquals(ColorSchemeChoice.DEFAULT, store.colorScheme.first())

        ColorSchemeChoice.entries.forEach { choice ->
            store.saveColorScheme(choice)
            assertEquals(choice, store.colorScheme.first())
        }

        dataStore.edit { it[stringPreferencesKey("color_scheme")] = "RAINBOW" }
        assertEquals(ColorSchemeChoice.DEFAULT, store.colorScheme.first())
    }

    @Test
    fun `the mask shows only the last four characters`() {
        assertEquals("••••1234", maskApiKey("cond_abcdef1234"))
    }

    private fun TestScope.newDataStore(name: String) =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.root.resolve("$name.preferences_pb") }
}
