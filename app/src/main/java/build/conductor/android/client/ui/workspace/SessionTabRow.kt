package build.conductor.android.client.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.conductor.android.client.data.AgentStatus

/** One tab for each session, then a tab that creates a session. [selectedIndex] is null when there is no session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionTabRow(sessions: List<SessionRow>, selectedIndex: Int?, onSelect: (String) -> Unit, onNewSession: () -> Unit) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex ?: sessions.size,
        edgePadding = 8.dp,
        indicator = {
            if (selectedIndex != null) {
                TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = true), width = Dp.Unspecified)
            }
        },
    ) {
        sessions.forEachIndexed { index, row ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(row.session.id) },
                text = { SessionTabLabel(row) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { stateDescription = statusDescription(row) },
            )
        }
        Tab(
            selected = false,
            onClick = onNewSession,
            icon = { Icon(Icons.Filled.Add, contentDescription = "New session") },
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionTabLabel(row: SessionRow) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SessionStatusMark(row)
        Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = TAB_TITLE_MAX_WIDTH))
    }
}

/** A spinner while the agent works, a dot when it needs the user, and nothing when it waits. */
@Composable
private fun SessionStatusMark(row: SessionRow) {
    when {
        row.hasOpenQuestion -> StatusDot(MaterialTheme.colorScheme.primary)
        row.status == AgentStatus.WORKING -> CircularProgressIndicator(modifier = Modifier.size(MARK_SIZE), strokeWidth = 1.5.dp)
        row.status == AgentStatus.ERROR -> StatusDot(MaterialTheme.colorScheme.error)
        else -> Unit
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(modifier = Modifier.size(DOT_SIZE).background(color, CircleShape))
}

private fun statusDescription(row: SessionRow): String = when {
    row.hasOpenQuestion -> "Question for you"
    row.status == AgentStatus.WORKING -> "Working"
    row.status == AgentStatus.IDLE -> "Waiting for you"
    row.status == AgentStatus.ERROR -> "Error"
    else -> ""
}

private val TAB_TITLE_MAX_WIDTH = 180.dp
private val MARK_SIZE = 12.dp
private val DOT_SIZE = 8.dp
