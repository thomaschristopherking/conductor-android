package build.conductor.android.client

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import build.conductor.android.client.notify.StatusNotifier
import build.conductor.android.client.ui.components.LoadingView
import build.conductor.android.client.ui.navigation.AppNavHost
import build.conductor.android.client.ui.navigation.WorkspaceRoute
import build.conductor.android.client.ui.theme.ConductorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A session that a notification asked to open. The nav host clears it after it navigates. */
    private val requestedSession = MutableStateFlow<WorkspaceRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedSession.value = sessionRouteFrom(intent)
        val container = (application as ConductorApplication).container
        if (savedInstanceState == null) container.appUpdater.check()
        setContent {
            val colorScheme by container.colorScheme.collectAsStateWithLifecycle()
            colorScheme?.let { choice -> ConductorTheme(choice) { AppContent(container) } }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { (application as ConductorApplication).watchScheduler.restartIfNeeded() }
    }

    @Composable
    private fun AppContent(container: AppContainer) {
        Surface {
            val apiKeyState by container.apiKeyState.collectAsStateWithLifecycle()
            when (apiKeyState) {
                ApiKeyState.Loading -> LoadingView()
                else -> AppNavHost(container, hasApiKey = apiKeyState is ApiKeyState.Present, requestedSession)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sessionRouteFrom(intent)?.let { requestedSession.value = it }
    }

    private fun sessionRouteFrom(intent: Intent): WorkspaceRoute? {
        val workspaceId = intent.getStringExtra(StatusNotifier.EXTRA_WORKSPACE_ID) ?: return null
        return WorkspaceRoute(workspaceId, intent.getStringExtra(StatusNotifier.EXTRA_SESSION_ID))
    }
}
