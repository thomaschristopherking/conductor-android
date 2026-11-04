package build.conductor.android.client.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.R
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.voice.VoiceController
import build.conductor.android.client.voice.VoicePhase
import build.conductor.android.client.voice.VoiceState
import build.conductor.android.client.voice.VoiceTarget
import build.conductor.android.client.voice.phaseLabel

/** The whole screen is one button, so the user can talk, interrupt or pause without looking. */
@Composable
fun VoiceScreen(controller: VoiceController, target: VoiceTarget, onBack: () -> Unit) {
    val voiceState by controller.state.collectAsStateWithLifecycle()
    val state = voiceState?.takeIf { it.target.sessionId == target.sessionId }
    var startProblem by remember { mutableStateOf<String?>(null) }
    val startVoice = rememberMicrophoneStart(onGranted = { startProblem = controller.start(target) }, onDenied = { startProblem = MICROPHONE_DENIED })
    LaunchedEffect(target) { if (state == null) startVoice() }
    val actions = VoiceActions(onTap = controller::tap, onStopAgent = controller::stopAgent, onStart = startVoice, onEnd = { controller.end(); onBack() }, onBack = onBack)
    VoiceContent(target, state?.takeIf { it.phase != VoicePhase.ENDED }, state?.problem ?: startProblem, actions)
}

/** What the voice screen asks of the app. [onEnd] ends voice mode and leaves; [onBack] leaves while voice mode continues. */
internal class VoiceActions(
    val onTap: () -> Unit,
    val onStopAgent: () -> Unit,
    val onStart: () -> Unit,
    val onEnd: () -> Unit,
    val onBack: () -> Unit,
)

/** [state] is null while voice mode is off; [problem] then tells why. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceContent(target: VoiceTarget, state: VoiceState?, problem: String?, actions: VoiceActions) {
    Scaffold(topBar = { TopAppBar(title = { VoiceTitle(target) }, navigationIcon = { BackButton(actions.onBack) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state == null) VoiceOff(problem, onStart = actions.onStart, onBack = actions.onBack) else VoiceOn(state, actions)
        }
    }
}

/** Asks for the microphone first; the result decides if voice mode starts. */
@Composable
private fun rememberMicrophoneStart(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> if (isGranted) onGranted() else onDenied() }
    return {
        val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (isGranted) onGranted() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
private fun VoiceTitle(target: VoiceTarget) {
    Column {
        Text("Voice", maxLines = 1)
        Text(target.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back. Voice mode continues.") }
}

@Composable
private fun VoiceOn(state: VoiceState, actions: VoiceActions) {
    Column(modifier = Modifier.fillMaxSize()) {
        TapArea(state, onTap = actions.onTap, modifier = Modifier.weight(1f).fillMaxWidth())
        VoiceButtons(state, onStopAgent = actions.onStopAgent, onEnd = actions.onEnd)
    }
}

@Composable
private fun TapArea(state: VoiceState, onTap: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onTap).semantics { contentDescription = tapDescription(state.phase) }.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PhaseCircle(state.phase)
        Spacer(Modifier.height(24.dp))
        Text(agentLine(state), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        state.caption?.let { Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, maxLines = CAPTION_MAX_LINES, overflow = TextOverflow.Ellipsis) }
        state.problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(24.dp))
        Text(tapHint(state.phase), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text(COMMAND_HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** The circle pulses while the microphone is open, so a glance shows that the phone listens. */
@Composable
private fun PhaseCircle(phase: VoicePhase) {
    val isListening = phase == VoicePhase.LISTENING || phase == VoicePhase.HEARING
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) PULSE_SCALE else 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
        label = "pulse",
    )
    Surface(shape = CircleShape, color = phaseColor(phase), modifier = Modifier.size(CIRCLE_SIZE).scale(pulse)) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(48.dp))
                Text(phaseLabel(phase), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun phaseColor(phase: VoicePhase): Color = when (phase) {
    VoicePhase.LISTENING, VoicePhase.HEARING -> MaterialTheme.colorScheme.primaryContainer
    VoicePhase.SPEAKING -> MaterialTheme.colorScheme.tertiaryContainer
    VoicePhase.CONFIRMING, VoicePhase.SENDING -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun VoiceButtons(state: VoiceState, onStopAgent: () -> Unit, onEnd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onStopAgent, enabled = state.agentStatus == AgentStatus.WORKING, modifier = Modifier.weight(1f)) { Text("Stop agent") }
        Button(onClick = onEnd, modifier = Modifier.weight(1f)) { Text("End voice") }
    }
}

@Composable
private fun VoiceOff(problem: String?, onStart: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Voice is off.", style = MaterialTheme.typography.titleLarge)
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Button(onClick = onStart) { Text("Start voice") }
        OutlinedButton(onClick = onBack) { Text("Back to the chat") }
    }
}

private fun agentLine(state: VoiceState): String = when {
    state.hasOpenQuestion -> "The agent waits for your answer."
    state.agentStatus == AgentStatus.WORKING -> "The agent is working."
    state.agentStatus == AgentStatus.IDLE -> "The agent is waiting for you."
    state.agentStatus == AgentStatus.ERROR -> "The agent stopped with an error."
    else -> ""
}

private fun tapHint(phase: VoicePhase): String = when (phase) {
    VoicePhase.SPEAKING -> "Speak or tap to interrupt."
    VoicePhase.LISTENING, VoicePhase.HEARING -> "Speak now. Tap to pause the microphone."
    VoicePhase.CONFIRMING -> "Keep talking to add more, say \"cancel\", or tap to send now."
    VoicePhase.PAUSED -> "Tap to talk."
    else -> ""
}

private fun tapDescription(phase: VoicePhase): String = when (phase) {
    VoicePhase.SPEAKING -> "Interrupt"
    VoicePhase.LISTENING, VoicePhase.HEARING -> "Pause the microphone"
    VoicePhase.CONFIRMING -> "Send now"
    else -> "Talk"
}

private const val MICROPHONE_DENIED = "Voice mode needs the microphone permission."
private const val COMMAND_HINT = "Say \"stop\", \"repeat\", \"continue\", \"status\", \"pause\" or \"end voice\"."
private const val CAPTION_MAX_LINES = 6
private const val PULSE_SCALE = 1.08f
private const val PULSE_MILLIS = 700
private val CIRCLE_SIZE = 200.dp
