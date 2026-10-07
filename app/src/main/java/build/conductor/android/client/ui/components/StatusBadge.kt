package build.conductor.android.client.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.WorkspaceState

enum class BadgeTone { POSITIVE, ACTIVE, NEUTRAL, NEGATIVE }

@Composable
fun StatusBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val (container, content) = badgeColors(tone)
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
private fun badgeColors(tone: BadgeTone): Pair<Color, Color> = with(MaterialTheme.colorScheme) {
    when (tone) {
        BadgeTone.POSITIVE -> tertiaryContainer to onTertiaryContainer
        BadgeTone.ACTIVE -> primaryContainer to onPrimaryContainer
        BadgeTone.NEUTRAL -> surfaceVariant to onSurfaceVariant
        BadgeTone.NEGATIVE -> errorContainer to onErrorContainer
    }
}

@Composable
fun AgentStatusBadge(status: AgentStatus, modifier: Modifier = Modifier) {
    val (text, tone) = when (status) {
        AgentStatus.WORKING -> "Working" to BadgeTone.ACTIVE
        AgentStatus.IDLE -> "Waiting for you" to BadgeTone.POSITIVE
        AgentStatus.ERROR -> "Error" to BadgeTone.NEGATIVE
        AgentStatus.UNKNOWN -> "Unknown" to BadgeTone.NEUTRAL
    }
    StatusBadge(text, tone, modifier)
}

/** The API reports "working" while the agent waits on a question, so the question takes priority. */
@Composable
fun SessionStatusBadge(status: AgentStatus, hasOpenQuestion: Boolean, modifier: Modifier = Modifier) {
    if (hasOpenQuestion) StatusBadge("Question for you", BadgeTone.POSITIVE, modifier) else AgentStatusBadge(status, modifier)
}

@Composable
fun WorkspaceStateBadge(state: WorkspaceState, lifecycleStep: String?, modifier: Modifier = Modifier) {
    val label = state.name.lowercase().replaceFirstChar { it.uppercase() }
    val text = lifecycleStep?.takeIf { state == WorkspaceState.INITIALIZING || state == WorkspaceState.UPDATING }
        ?.let { "$label · ${it.replace('_', ' ')}" } ?: label
    val tone = when (state) {
        WorkspaceState.READY -> BadgeTone.POSITIVE
        WorkspaceState.INITIALIZING, WorkspaceState.UPDATING -> BadgeTone.ACTIVE
        WorkspaceState.DELETED -> BadgeTone.NEGATIVE
        else -> BadgeTone.NEUTRAL
    }
    StatusBadge(text, tone, modifier)
}
