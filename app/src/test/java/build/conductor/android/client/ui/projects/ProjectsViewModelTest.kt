@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.ui.projects

import kotlinx.coroutines.ExperimentalCoroutinesApi
import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.MainDispatcherRule
import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.api.Project
import build.conductor.android.client.ui.components.LoadState
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProjectsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeConductorRepository()

    @Test
    fun `projects load sorted by name`() = runTest {
        repository.projectsResult = { listOf(Project("2", "zeta", "r"), Project("1", "Alpha", "r")) }

        val viewModel = ProjectsViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("Alpha", "zeta"), (viewModel.uiState.value.projects as LoadState.Loaded).value.map { it.name })
    }

    @Test
    fun `a 429 shows a retryable error`() = runTest {
        repository.projectsResult = { throw ApiException.RateLimited("Too many requests") }

        val viewModel = ProjectsViewModel(repository)
        advanceUntilIdle()

        val failed = viewModel.uiState.value.projects as LoadState.Failed
        assertEquals("Too many requests", failed.message)
        assertTrue(failed.isRetryable)
    }
}
