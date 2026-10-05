package build.conductor.android.client

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.notify.StatusNotifier
import build.conductor.android.client.ui.components.LoadingView
import build.conductor.android.client.ui.navigation.AppNavHost
import build.conductor.android.client.ui.navigation.SessionRoute
import build.conductor.android.client.ui.theme.ConductorTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** A session that a notification asked to open. The nav host clears it after it navigates. */
    private val requestedSession = MutableStateFlow<SessionRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedSession.value = sessionRouteFrom(intent)
        val container = (application as ConductorApplication).container
        setContent {
            ConductorTheme {
                Surface {
                    val apiKeyState by container.apiKeyState.collectAsStateWithLifecycle()
                    when (apiKeyState) {
                        ApiKeyState.Loading -> LoadingView()
                        else -> AppNavHost(container, hasApiKey = apiKeyState is ApiKeyState.Present, requestedSession)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sessionRouteFrom(intent)?.let { requestedSession.value = it }
    }

    private fun sessionRouteFrom(intent: Intent): SessionRoute? {
        val sessionId = intent.getStringExtra(StatusNotifier.EXTRA_SESSION_ID) ?: return null
        return SessionRoute(sessionId, intent.getStringExtra(StatusNotifier.EXTRA_SESSION_TITLE) ?: "Session")
    }
}
