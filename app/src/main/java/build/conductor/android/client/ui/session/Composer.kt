package build.conductor.android.client.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import build.conductor.android.client.data.AgentStatus

/** What the composer does with a key press, a chosen command, a chosen model or effort, and the send button. */
data class ComposerActions(
    val onDraftChange: (String) -> Unit,
    val onChooseCommand: (String) -> Unit,
    val onSwitchModel: (String) -> Unit,
    val onSwitchEffort: (String?) -> Unit,
    val onSend: () -> Unit,
)

/** The draft field and the send button. While the draft types a command name after `/`, the matching commands show above them. */
@Composable
internal fun Composer(state: SessionUiState, actions: ComposerActions) {
    val suggestions = remember(state.draft, state.slashCommands) { suggestSlashCommands(state.draft, state.slashCommands) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (suggestions.isNotEmpty()) SlashCommandList(suggestions, actions.onChooseCommand)
        state.modelSelection?.let { ModelSwitcher(it, state.canSwitchModel, actions) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DraftField(state, actions.onDraftChange, Modifier.weight(1f))
            FilledIconButton(onClick = actions.onSend, enabled = state.draft.isNotBlank() && !state.isSending) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

/** A draft that changes outside the field, such as a chosen command, puts the cursor at its end. */
@Composable
private fun DraftField(state: SessionUiState, onDraftChange: (String) -> Unit, modifier: Modifier) {
    var field by remember { mutableStateOf(TextFieldValue(state.draft)) }
    val value = if (field.text == state.draft) field else TextFieldValue(state.draft, TextRange(state.draft.length))
    OutlinedTextField(
        value = value,
        onValueChange = { field = it; onDraftChange(it.text) },
        placeholder = { Text(composerPlaceholder(state)) },
        maxLines = COMPOSER_MAX_LINES,
        modifier = modifier,
    )
}

@Composable
private fun SlashCommandList(names: List<String>, onChoose: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.heightIn(max = SLASH_COMMAND_LIST_MAX_HEIGHT).testTag(SLASH_COMMAND_LIST_TAG)) {
            items(names, key = { it }) { name ->
                Text(
                    text = slashCommandLabel(name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { onChoose(name) }.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
    HorizontalDivider()
}

private fun composerPlaceholder(state: SessionUiState): String = when {
    state.openQuestion != null -> "Or answer in your own words…"
    state.status == AgentStatus.WORKING -> "Steer the agent…"
    else -> "Message the agent…"
}

internal const val SLASH_COMMAND_LIST_TAG = "slash-command-list"
private val SLASH_COMMAND_LIST_MAX_HEIGHT = 240.dp
private const val COMPOSER_MAX_LINES = 6
