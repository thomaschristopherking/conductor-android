package build.conductor.android.client.ui.session

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import build.conductor.android.client.data.transcript.Delivery
import build.conductor.android.client.data.transcript.TranscriptItem
import com.mikepenz.markdown.m3.Markdown
import java.util.Locale

@Composable
fun TranscriptRow(item: TranscriptItem) {
    when (item) {
        is TranscriptItem.UserPrompt -> UserPromptRow(item)
        is TranscriptItem.AssistantText -> Markdown(content = item.markdown, modifier = Modifier.fillMaxWidth())
        is TranscriptItem.ToolCall -> ToolCallRow(item)
        is TranscriptItem.ToolResult -> CollapsibleText(item.output.ifBlank { "(no output)" }, isError = item.isError, isMonospace = true)
        is TranscriptItem.TurnEnd -> TurnEndRow(item)
        is TranscriptItem.Notice -> CollapsibleText(item.text, isError = false, isMonospace = false)
    }
}

@Composable
private fun UserPromptRow(item: TranscriptItem.UserPrompt) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 520.dp).padding(start = 32.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(item.text, style = MaterialTheme.typography.bodyMedium)
                deliveryLabel(item.delivery)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
                }
            }
        }
    }
}

private fun deliveryLabel(delivery: Delivery): String? = when (delivery) {
    Delivery.SENDING -> "Sending…"
    Delivery.QUEUED -> "Queued"
    Delivery.SENT -> null
}

@Composable
private fun ToolCallRow(item: TranscriptItem.ToolCall) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.toolName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(item.summary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Shows two lines; a tap shows the full text. */
@Composable
private fun CollapsibleText(text: String, isError: Boolean, isMonospace: Boolean) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = if (isMonospace) FontFamily.Monospace else null,
        fontStyle = if (isMonospace) null else FontStyle.Italic,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (isExpanded) Int.MAX_VALUE else COLLAPSED_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable { isExpanded = !isExpanded }.padding(start = 12.dp),
    )
}

@Composable
private fun TurnEndRow(item: TranscriptItem.TurnEnd) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        HorizontalDivider()
        Text(
            text = turnEndLabel(item),
            style = MaterialTheme.typography.labelSmall,
            color = if (item.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
        )
    }
}

internal fun turnEndLabel(item: TranscriptItem.TurnEnd): String {
    val parts = mutableListOf(if (item.isError) "Turn failed" else "Turn finished")
    item.durationMillis?.let { parts += formatDuration(it) }
    item.costUsd?.let { parts += String.format(Locale.US, "$%.2f", it) }
    return parts.joinToString(" · ")
}

internal fun formatDuration(millis: Long): String {
    val totalSeconds = millis / MILLIS_PER_SECOND
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    return if (minutes > 0) "${minutes}m ${totalSeconds % SECONDS_PER_MINUTE}s" else "${totalSeconds}s"
}

private const val COLLAPSED_LINES = 2
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
