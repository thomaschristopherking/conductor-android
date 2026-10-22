package build.conductor.android.client.notify

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import build.conductor.android.client.FakeConductorRepository
import build.conductor.android.client.data.QuestionScanner
import build.conductor.android.client.data.api.Session
import build.conductor.android.client.data.settings.WorkspaceSubscriptionStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceWatcherTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val repository = FakeConductorRepository().apply {
        sessionsInWorkspace = listOf(Session(SESSION_ID, "conductor://1", "Fix CI"))
        sessionStatuses = mutableMapOf(SESSION_ID to "working")
    }
    private val alerts = mutableListOf<SessionAlert>()

    @Test
    fun `an agent that finishes is notified once, and a quiet workspace reads no sessions`() = runTest {
        val (store, watcher) = newWatcher()
        store.subscribe("w1", "Workspace w1", mapOf(SESSION_ID to "working"))
        assertTrue(watcher.check())

        repository.sessionStatuses[SESSION_ID] = "idle"
        repository.workspaceLastActivityAt = "2026-10-05T07:00:00Z"
        assertFalse(watcher.check())
        assertFalse(watcher.check())

        assertEquals(listOf(SessionAlert("w1", "Workspace w1", SESSION_ID, "Fix CI", AgentAlert.FINISHED)), alerts)
        assertEquals(2, repository.sessionsRequestCount)
    }

    @Test
    fun `a question is notified once`() = runTest {
        val (store, watcher) = newWatcher()
        store.subscribe("w1", "Workspace w1", mapOf(SESSION_ID to "working"))
        repository.addQuestion("t1")

        watcher.check()
        repository.addAgentText("Still waiting")
        watcher.check()

        assertEquals(listOf(AgentAlert.ASKED), alerts.map { it.alert })
    }

    @Test
    fun `an archived workspace is unsubscribed`() = runTest {
        val (store, watcher) = newWatcher()
        store.subscribe("w1", "Workspace w1", emptyMap())
        repository.workspaceState = "archived"

        watcher.check()

        assertTrue(store.snapshot().isEmpty())
    }

    private fun TestScope.newWatcher(): Pair<WorkspaceSubscriptionStore, WorkspaceWatcher> {
        val store = WorkspaceSubscriptionStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.root.resolve("watch.preferences_pb") })
        return store to WorkspaceWatcher(repository, QuestionScanner(repository), store) { alerts += it }
    }

    private companion object {
        const val SESSION_ID = FakeConductorRepository.SESSION_ID
    }
}
