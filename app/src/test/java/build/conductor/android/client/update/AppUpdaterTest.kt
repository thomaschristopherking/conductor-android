@file:OptIn(ExperimentalCoroutinesApi::class)

package build.conductor.android.client.update

import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.github.AppRelease
import build.conductor.android.client.data.github.FileChange
import build.conductor.android.client.data.github.GitHubRepository
import build.conductor.android.client.data.github.GitHubUser
import build.conductor.android.client.data.github.PullRequestStack
import build.conductor.android.client.data.github.RepositoryName
import build.conductor.android.client.data.github.StackPosition
import build.conductor.android.client.data.github.WorkspaceKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class AppUpdaterTest {
    private val build15 = AppRelease(15, "v0.1.0-release.15", "https://example.com/conductor-android.apk", 2_000_000)
    private val github = ReleaseGitHubRepository(build15)
    private val downloader = FakeDownloader()
    private val installer = FakeInstaller(installedBuild = 14)

    @Test
    fun `a release with a higher build number is available, and the same build is up to date`() = runTest {
        val updater = checkedUpdater()
        assertEquals(UpdateState.Available(build15), updater.updateState.value)

        github.release = build15.copy(buildNumber = 14)
        updater.check()
        runCurrent()
        assertEquals(UpdateState.UpToDate, updater.updateState.value)
    }

    @Test
    fun `a failed check has no release to install`() = runTest {
        github.failure = ApiException.Server(502, "GitHub is not available (HTTP 502). Try again.")

        val updater = checkedUpdater()

        val failed = updater.updateState.value as UpdateState.Failed
        assertEquals(null, failed.newerRelease)
        assertTrue(failed.message.endsWith("GitHub is not available (HTTP 502). Try again."))
    }

    @Test
    fun `without the install permission, the update opens the Android settings and downloads nothing`() = runTest {
        installer.isAllowed = false
        val updater = checkedUpdater()

        updater.update()
        runCurrent()

        assertEquals(UpdateState.NeedsPermission(build15), updater.updateState.value)
        assertEquals(1, installer.permissionRequests)
        assertTrue(downloader.requests.isEmpty())
    }

    @Test
    fun `the update shows the download progress, then hands the file to Android`() = runTest {
        val updater = checkedUpdater()

        updater.update()
        runCurrent()
        assertEquals(UpdateState.Downloading(build15, 0.5f), updater.updateState.value)

        downloader.finish.complete(Unit)
        runCurrent()

        assertEquals(UpdateState.Installing(build15), updater.updateState.value)
        assertEquals(listOf(downloader.apk), installer.installed)
    }

    @Test
    fun `a failed download keeps the release, so the user can try again`() = runTest {
        val updater = checkedUpdater()
        downloader.failure = IOException("GitHub answered HTTP 404.")

        updater.update()
        downloader.finish.complete(Unit)
        runCurrent()

        assertEquals(UpdateState.Failed("The download failed. GitHub answered HTTP 404.", build15), updater.updateState.value)
    }

    @Test
    fun `a cancelled Android dialog makes the update available again, and a failure shows the message of Android`() = runTest {
        val updater = checkedUpdater()
        updater.update()
        downloader.finish.complete(Unit)
        runCurrent()

        updater.onInstallCancelled()
        assertEquals(UpdateState.Available(build15), updater.updateState.value)

        updater.onInstallFailed("INSTALL_FAILED_UPDATE_INCOMPATIBLE")
        assertEquals(UpdateState.Failed("INSTALL_FAILED_UPDATE_INCOMPATIBLE", build15), updater.updateState.value)
    }

    private fun TestScope.checkedUpdater() = AppUpdater(github, downloader, installer, backgroundScope).also {
        it.check()
        runCurrent()
    }
}

private class ReleaseGitHubRepository(var release: AppRelease?) : GitHubRepository {
    var failure: ApiException? = null

    override suspend fun newestAppRelease(repository: RepositoryName): AppRelease? {
        failure?.let { throw it }
        return release
    }

    override suspend fun testToken(token: String) = GitHubUser("octocat")
    override suspend fun workspaceStack(repository: RepositoryName, workspace: WorkspaceKey): PullRequestStack? = null
    override suspend fun stackPositions(repository: RepositoryName, workspaces: List<WorkspaceKey>): Map<WorkspaceKey, StackPosition> = emptyMap()
    override suspend fun changedFiles(repository: RepositoryName, number: Int): List<FileChange> = emptyList()
}

/** Reports half of the file, then waits for [finish]. */
private class FakeDownloader : ApkDownloader {
    val apk = File("conductor-android.apk")
    val finish = CompletableDeferred<Unit>()
    var failure: IOException? = null
    val requests = mutableListOf<AppRelease>()

    override suspend fun download(release: AppRelease, onProgress: (Float?) -> Unit): File {
        requests += release
        onProgress(0.5f)
        finish.await()
        failure?.let { throw it }
        return apk
    }
}

private class FakeInstaller(override val installedBuild: Int) : ApkInstaller {
    var isAllowed = true
    var permissionRequests = 0
    val installed = mutableListOf<File>()

    override fun canInstall() = isAllowed

    override fun openInstallPermission() {
        permissionRequests++
    }

    override suspend fun install(apk: File) {
        installed += apk
    }
}
