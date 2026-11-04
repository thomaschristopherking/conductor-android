package build.conductor.android.client.ui.voice

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.R
import build.conductor.android.client.voice.VoiceController
import build.conductor.android.client.voice.VoicePhase

/** Opens voice mode for a session; the icon takes the accent colour while voice mode talks to that session. */
@Composable
fun VoiceButton(controller: VoiceController, sessionId: String, onOpen: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val isActive = state?.let { it.target.sessionId == sessionId && it.phase != VoicePhase.ENDED } == true
    IconButton(onClick = onOpen) {
        Icon(
            painterResource(R.drawable.ic_mic),
            contentDescription = if (isActive) "Voice mode is on. Open it." else "Talk to the agent by voice",
            tint = if (isActive) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}
