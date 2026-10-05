package build.conductor.android.client.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

        store.setStarred("s1", "Fix CI", isStarred = true)
        store.recordStatuses(mapOf("s1" to "working", "s2" to "idle"))
        assertEquals(StarredSession("Fix CI", "working"), store.snapshot()["s1"])
        assertEquals(setOf("s1"), store.starredIds.first())
        store.setStarred("s1", "Fix CI", isStarred = false)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun `the mask shows only the last four characters`() {
        assertEquals("••••1234", maskApiKey("cond_abcdef1234"))
    }

    private fun TestScope.newDataStore(name: String) =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.root.resolve("$name.preferences_pb") }
}
