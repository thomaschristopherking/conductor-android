package build.conductor.android.client.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import build.conductor.android.client.data.ClaudeModelSwitch
import build.conductor.android.client.data.ModelSelection

/** The model and effort of the session. A choice sends a `/model` or `/effort` prompt, which applies from the next turn. */
@Composable
internal fun ModelSwitcher(selection: ModelSelection, isEnabled: Boolean, actions: ComposerActions) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MenuButton("Model", selection.model, ClaudeModelSwitch.models.map { it to it }, isEnabled) { actions.onSwitchModel(it) }
        val efforts = listOf<Pair<String?, String>>(null to DEFAULT_EFFORT_LABEL) + ClaudeModelSwitch.efforts.map { it to it }
        MenuButton("Effort", selection.effort ?: DEFAULT_EFFORT_LABEL, efforts, isEnabled) { actions.onSwitchEffort(it) }
    }
}

/** A text button that opens a menu. [options] holds pairs of value and label. */
@Composable
private fun <T> MenuButton(name: String, displayValue: String, options: List<Pair<T, String>>, isEnabled: Boolean, onSelect: (T) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { isExpanded = true },
            enabled = isEnabled,
            modifier = Modifier.semantics { contentDescription = "$name: $displayValue" },
        ) {
            Text(displayValue)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { isExpanded = false; onSelect(value) })
            }
        }
    }
}

private const val DEFAULT_EFFORT_LABEL = "Default effort"
