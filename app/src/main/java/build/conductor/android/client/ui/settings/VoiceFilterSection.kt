package build.conductor.android.client.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.voice.VoiceFilter

/** The voice filter applies when voice mode starts, so a change needs a new voice conversation. */
@Composable
internal fun VoiceFilterSection(viewModel: VoiceSettingsViewModel) {
    val selected by viewModel.voiceFilter.collectAsStateWithLifecycle()
    Text("Voice filter", style = MaterialTheme.typography.titleMedium)
    Text(
        "Voice mode can ignore voices that are far from the phone. It cannot tell your voice from a voice near you. A change applies the next time that voice mode starts.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(modifier = Modifier.selectableGroup()) {
        VoiceFilter.entries.forEach { filter ->
            VoiceFilterOption(filter, isSelected = filter == selected, onClick = { viewModel.selectVoiceFilter(filter) })
        }
    }
}

@Composable
private fun VoiceFilterOption(filter: VoiceFilter, isSelected: Boolean, onClick: () -> Unit) {
    val (label, description) = when (filter) {
        VoiceFilter.ANY -> "Any voice" to "Reacts to every voice that the phone hears."
        VoiceFilter.NEAR -> "Near voices" to "Ignores a voice across the room, for example a TV."
        VoiceFilter.VERY_NEAR -> "Very near voices" to "Reacts only when you speak close to the phone or into a headset."
    }
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
