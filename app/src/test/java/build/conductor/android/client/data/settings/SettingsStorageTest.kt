package build.conductor.android.client.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import build.conductor.android.client.data.QuestionScan
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
    fun `starred sessions are added, updated and removed`() = runTest {
        val store = StarredSessionStore(newDataStore("starred"))

        store.star("s1", "Fix CI", currentStatus = "idle")
        assertEquals(StarredSession("Fix CI", "idle"), store.snapshot()["s1"])
        store.recordStatuses(mapOf("s1" to "working", "s2" to "idle"))
        assertEquals(StarredSession("Fix CI", "working"), store.snapshot()["s1"])
        assertEquals(setOf("s1"), store.starredIds.first())
        store.unstar("s1")
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
    fun `starred sessions keep their question scan and the last question that the user saw`() = runTest {
        val store = StarredSessionStore(newDataStore("questions"))
        store.star("s1", "Fix CI", currentStatus = "working")

        store.recordQuestionScans(mapOf("s1" to QuestionScan("m5", openQuestionId = "t1"), "gone" to QuestionScan("m1", null)))
        assertEquals(StarredSession("Fix CI", "working", QuestionScan("m5", "t1"), seenQuestionId = "t1"), store.snapshot()["s1"])
        store.recordQuestionScans(mapOf("s1" to QuestionScan("m7", openQuestionId = null)))
        assertEquals("t1", store.snapshot()["s1"]?.seenQuestionId)
        store.recordSeenQuestion("s1", "t3")
        assertEquals("t3", store.snapshot()["s1"]?.seenQuestionId)
        assertEquals(setOf("s1"), store.snapshot().keys)
    }

    @Test
    fun `the mask shows only the last four characters`() {
        assertEquals("••••1234", maskApiKey("cond_abcdef1234"))
    }

    private fun TestScope.newDataStore(name: String) =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.root.resolve("$name.preferences_pb") }
}
