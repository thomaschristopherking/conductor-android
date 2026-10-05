package build.conductor.android.client

import android.app.Application
import build.conductor.android.client.notify.StarredSessionScheduler
import kotlinx.coroutines.launch

class ConductorApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        val scheduler = StarredSessionScheduler(this, container.starredSessionStore)
        container.applicationScope.launch { scheduler.followStarredSessions() }
    }
}
