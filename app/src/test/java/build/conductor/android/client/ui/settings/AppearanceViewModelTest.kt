package build.conductor.android.client.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.settings.AppearanceStore
import build.conductor.android.client.data.settings.ColorSchemeChoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppearanceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a selected colour scheme is saved and shown as selected`() = runTest {
        val store = AppearanceStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.root.resolve("settings.preferences_pb") })
        val viewModel = AppearanceViewModel(store)

        viewModel.colorScheme.test {
            assertEquals(ColorSchemeChoice.DEFAULT, awaitItem())
            viewModel.selectColorScheme(ColorSchemeChoice.NEAPOLITAN)
            assertEquals(ColorSchemeChoice.NEAPOLITAN, awaitItem())
        }
        assertEquals(ColorSchemeChoice.NEAPOLITAN, store.colorScheme.first())
    }
}
