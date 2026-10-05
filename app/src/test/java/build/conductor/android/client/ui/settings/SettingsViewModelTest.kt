package build.conductor.android.client.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import build.conductor.android.client.ApiKeyState
import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.settings.AesGcmCipher
import build.conductor.android.client.data.settings.ApiKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val repository = FakeConductorRepository()
    private val cipher = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().let { key -> AesGcmCipher { key } }

    private fun TestScope.keyStore() =
        ApiKeyStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.root.resolve("settings.preferences_pb") }, cipher)

    @Test
    fun `a key that passes the test call is saved`() = runTest {
        val store = keyStore()
        val viewModel = SettingsViewModel(repository, store, MutableStateFlow(ApiKeyState.Missing))
        viewModel.onKeyInputChange(FakeConductorRepository.VALID_KEY)

        viewModel.eventFlow.test {
            viewModel.testAndSave()
            assertEquals(SettingsEvent.KeySaved, awaitItem())
        }
        assertEquals(FakeConductorRepository.VALID_KEY, store.apiKey.first())
    }

    @Test
    fun `a rejected key is not saved and shows an error`() = runTest {
        val store = keyStore()
        val viewModel = SettingsViewModel(repository, store, MutableStateFlow(ApiKeyState.Missing))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onKeyInputChange("wrong-key")

        viewModel.testAndSave()
        advanceUntilIdle()

        assertNull(store.apiKey.first())
        assertEquals("Conductor rejected this key. Check it and try again.", viewModel.uiState.value.error)
    }
}
