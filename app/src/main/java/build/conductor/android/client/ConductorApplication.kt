package build.conductor.android.client

import android.app.Application

class ConductorApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
