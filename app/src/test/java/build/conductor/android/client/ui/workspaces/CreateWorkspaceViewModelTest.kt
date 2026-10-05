package build.conductor.android.client.ui.workspaces

import app.cash.turbine.test
import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.ModelSelection
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreateWorkspaceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeConductorRepository()

    @Test
    fun `the first favourite becomes the default model`() = runTest {
        val viewModel = CreateWorkspaceViewModel(repository, "p1")
        advanceUntilIdle()

        assertEquals(ModelSelection("codex", "gpt-6.1-sol", "high"), viewModel.uiState.value.selection)
    }

    @Test
    fun `submit sends the form and emits the created workspace`() = runTest {
        val viewModel = CreateWorkspaceViewModel(repository, "p1")
        advanceUntilIdle()
        viewModel.onNameChange("fix-ci")
        viewModel.onSelectionChange(ModelSelection("claude", "opus-5-5-1m", "max"))
        viewModel.onMessageChange("Fix the CI")

        viewModel.createdEvents.test {
            viewModel.submit()
            assertEquals("s-new", awaitItem().sessionId)
        }
        val request = repository.createdWorkspaces.single()
        assertEquals("fix-ci", request.name)
        assertEquals("opus-5-5-1m", request.model)
        assertEquals("max", request.effort)
        assertEquals("Fix the CI", request.message)
    }
}
