package build.conductor.android.client.ui.settings

import androidx.lifecycle.ViewModel
import build.conductor.android.client.update.AppUpdater
import build.conductor.android.client.update.UpdateState
import kotlinx.coroutines.flow.StateFlow

/** The update section of Settings. The [AppUpdater] keeps the state, so a download goes on when the user leaves Settings. */
class AppUpdateViewModel(private val updater: AppUpdater) : ViewModel() {
    val updateState: StateFlow<UpdateState> = updater.updateState
    val installedBuild: Int = updater.installedBuild

    fun check() = updater.check()

    fun update() = updater.update()
}
