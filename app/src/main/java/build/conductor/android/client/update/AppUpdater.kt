package build.conductor.android.client.update

import build.conductor.android.client.data.api.ApiException
import build.conductor.android.client.data.github.APP_REPOSITORY
import build.conductor.android.client.data.github.AppRelease
import build.conductor.android.client.data.github.GitHubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

sealed interface UpdateState {
    data object NotChecked : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState

    /** [fraction] is null when the server sends no length. */
    data class Downloading(val release: AppRelease, val fraction: Float?) : UpdateState

    /** Android asks the user to allow installs from this app. */
    data class NeedsPermission(val release: AppRelease) : UpdateState

    /** Android shows its own dialog to confirm the update. */
    data class Installing(val release: AppRelease) : UpdateState

    /** A null [release] means that the check failed. */
    data class Failed(val message: String, val release: AppRelease?) : UpdateState
}

/** The newer build that the user can install, or null. */
val UpdateState.newerRelease: AppRelease?
    get() = when (this) {
        is UpdateState.Available -> release
        is UpdateState.Downloading -> release
        is UpdateState.NeedsPermission -> release
        is UpdateState.Installing -> release
        is UpdateState.Failed -> release
        UpdateState.NotChecked, UpdateState.Checking, UpdateState.UpToDate -> null
    }

/** Saves the APK of a release to a file on the phone. Throws [IOException] on failure. */
fun interface ApkDownloader {
    suspend fun download(release: AppRelease, onProgress: (Float?) -> Unit): File
}

/** The Android side of an update. */
interface ApkInstaller {
    val installedBuild: Int
    fun canInstall(): Boolean
    fun openInstallPermission()

    /** Hands the APK to Android. The result comes back through [AppUpdater.onInstallCancelled] or [AppUpdater.onInstallFailed]. */
    suspend fun install(apk: File)
}

/** Finds a newer build on GitHub and installs it. Every screen shares one instance. */
class AppUpdater(
    private val github: GitHubRepository,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<UpdateState>(UpdateState.NotChecked)
    val updateState: StateFlow<UpdateState> = state.asStateFlow()
    val installedBuild: Int get() = installer.installedBuild

    fun check() {
        if (isBusy()) return
        state.value = UpdateState.Checking
        scope.launch { state.value = findUpdate() }
    }

    private fun isBusy() = state.value.let { it is UpdateState.Checking || it is UpdateState.Downloading || it is UpdateState.Installing }

    private suspend fun findUpdate(): UpdateState = try {
        github.newestAppRelease(APP_REPOSITORY)?.takeIf { it.buildNumber > installer.installedBuild }?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
    } catch (exception: ApiException) {
        UpdateState.Failed("The app could not look for a new build. ${exception.message}", release = null)
    }

    fun update() {
        val release = state.value.newerRelease ?: return
        if (isBusy()) return
        if (!installer.canInstall()) {
            state.value = UpdateState.NeedsPermission(release)
            installer.openInstallPermission()
            return
        }
        state.value = UpdateState.Downloading(release, fraction = null)
        scope.launch { downloadAndInstall(release) }
    }

    private suspend fun downloadAndInstall(release: AppRelease) {
        val apk = try {
            downloader.download(release) { fraction -> state.value = UpdateState.Downloading(release, fraction) }
        } catch (exception: IOException) {
            state.value = UpdateState.Failed("The download failed. ${exception.message.orEmpty()}".trim(), release)
            return
        }
        state.value = UpdateState.Installing(release)
        try {
            installer.install(apk)
        } catch (exception: IOException) {
            onInstallFailed("Android could not start the update. ${exception.message.orEmpty()}".trim())
        }
    }

    fun onInstallCancelled() {
        (state.value as? UpdateState.Installing)?.let { state.value = UpdateState.Available(it.release) }
    }

    fun onInstallFailed(message: String) {
        state.value = UpdateState.Failed(message, state.value.newerRelease)
    }
}
