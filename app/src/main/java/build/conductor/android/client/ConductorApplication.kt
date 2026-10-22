package build.conductor.android.client

import android.app.Application
import build.conductor.android.client.notify.WatchScheduler
import kotlinx.coroutines.launch

class ConductorApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    val watchScheduler: WatchScheduler by lazy { WatchScheduler(this, container.isWatching) }

    override fun onCreate() {
        super.onCreate()
        container.applicationScope.launch { watchScheduler.followSubscriptions() }
    }
}
