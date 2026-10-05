package build.conductor.android.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.ui.components.LoadingView
import build.conductor.android.client.ui.navigation.AppNavHost
import build.conductor.android.client.ui.theme.ConductorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ConductorApplication).container
        setContent {
            ConductorTheme {
                Surface {
                    val apiKeyState by container.apiKeyState.collectAsStateWithLifecycle()
                    when (apiKeyState) {
                        ApiKeyState.Loading -> LoadingView()
                        else -> AppNavHost(container, hasApiKey = apiKeyState is ApiKeyState.Present)
                    }
                }
            }
        }
    }
}
