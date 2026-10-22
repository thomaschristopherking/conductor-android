@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.data

import app.cash.turbine.test
import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import build.conductor.android.client.data.api.ApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.SocketTimeoutException

class WorkspaceArchiverTest {
    private val repository = FakeConductorRepository()
    private val alpha = workspace("alpha", "Alpha")
    private val beta = workspace("beta", "Beta")

    @Test
    fun `an archived workspace is pending at once and is sent when the undo window closes`() = runTest {
        val archiver = archiver()

        archiver.archive(alpha)
        runCurrent()
        assertEquals(setOf("alpha"), archiver.archiveState.value.pendingIds)
        assertEquals(alpha, archiver.archiveState.value.undoable)
        advanceTimeBy(WorkspaceArchiver.UNDO_WINDOW_MILLIS - 1)
        assertEquals(emptyList<String>(), repository.archivedWorkspaceIds)

        advanceTimeBy(2)

        assertEquals(listOf("alpha"), repository.archivedWorkspaceIds)
        assertEquals(ArchiveState(archivedIds = setOf("alpha")), archiver.archiveState.value)
    }

    @Test
    fun `undo inside the window sends no archive call`() = runTest {
        val archiver = archiver()

        archiver.archive(alpha)
        advanceTimeBy(WorkspaceArchiver.UNDO_WINDOW_MILLIS / 2)
        archiver.undo()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.archivedWorkspaceIds)
        assertEquals(ArchiveState(), archiver.archiveState.value)
    }

    @Test
    fun `a second archive sends the first at once and moves undo to the second`() = runTest {
        val archiver = archiver()

        archiver.archive(alpha)
        archiver.archive(beta)
        runCurrent()

        assertEquals(listOf("alpha"), repository.archivedWorkspaceIds)
        assertEquals(beta, archiver.archiveState.value.undoable)
        archiver.undo()
        advanceUntilIdle()
        assertEquals(listOf("alpha"), repository.archivedWorkspaceIds)
    }

    @Test
    fun `confirm twice sends one archive call`() = runTest {
        val archiver = archiver()

        archiver.archive(alpha)
        archiver.confirm()
        archiver.confirm()
        runCurrent()

        assertEquals(listOf("alpha"), repository.archivedWorkspaceIds)
    }

    @Test
    fun `a failed archive shows the workspace again and reports the failure`() = runTest {
        repository.archiveFailure = ApiException.Server(503, "down")
        val archiver = archiver()

        archiver.failureMessages.test {
            archiver.archive(alpha)
            archiver.confirm()
            assertEquals("Could not archive \"Alpha\". down", awaitItem())
        }
        assertEquals(ArchiveState(), archiver.archiveState.value)
    }

    @Test
    fun `a lost answer counts as archived when the workspace reads as archived`() = runTest {
        repository.archiveFailure = ApiException.Offline(SocketTimeoutException())
        repository.workspaceState = "archived"
        val archiver = archiver()

        archiver.archive(alpha)
        archiver.confirm()
        runCurrent()

        assertEquals(setOf("alpha"), archiver.archiveState.value.archivedIds)
    }

    @Test
    fun `a lost answer is a failure when the workspace is not archived`() = runTest {
        repository.archiveFailure = ApiException.Offline(SocketTimeoutException())
        val archiver = archiver()

        archiver.archive(alpha)
        archiver.confirm()
        runCurrent()

        assertEquals(ArchiveState(), archiver.archiveState.value)
    }

    @Test
    fun `an archived workspace cannot be archived again`() = runTest {
        val archiver = archiver()

        archiver.archive(alpha.copy(state = "archived"))

        assertNull(archiver.archiveState.value.undoable)
    }

    @Test
    fun `the visible list hides pending workspaces and shows finished ones as archived only when asked`() {
        val archive = ArchiveState(pendingIds = setOf("alpha"), archivedIds = setOf("beta"))
        val gamma = workspace("gamma", "Gamma")

        assertEquals(listOf("gamma"), archive.applyTo(listOf(alpha, beta, gamma), isShowingArchived = false).map { it.id })
        assertEquals(listOf("archived", "ready"), archive.applyTo(listOf(alpha, beta, gamma), isShowingArchived = true).map { it.state })
    }

    private fun TestScope.archiver() = WorkspaceArchiver(repository, backgroundScope)
}
