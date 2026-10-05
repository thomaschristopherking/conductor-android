package build.conductor.android.client.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.conductor.android.client.data.ModelCatalog
import build.conductor.android.client.data.ModelSelection

private const val DEFAULT_EFFORT_LABEL = "Agent default"

/** Agent, model and effort fields, with the user's favourite models as shortcuts. */
@Composable
fun ModelPicker(
    selection: ModelSelection,
    favorites: List<ModelSelection>,
    onSelectionChange: (ModelSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val agent = ModelCatalog.agent(selection.agent) ?: ModelCatalog.agents.first()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (favorites.isNotEmpty()) FavoriteChips(selection, favorites, onSelectionChange)
        DropdownField("Agent", agent.label, ModelCatalog.agents.map { it.id to it.label }) { id ->
            val chosen = ModelCatalog.agent(id) ?: return@DropdownField
            onSelectionChange(ModelSelection(chosen.id, chosen.defaultModel, null))
        }
        DropdownField("Model", selection.model, agent.models.map { it to it }) { model ->
            onSelectionChange(selection.copy(model = model))
        }
        val efforts = listOf("" to DEFAULT_EFFORT_LABEL) + agent.efforts.map { it to it }
        DropdownField("Effort", selection.effort ?: DEFAULT_EFFORT_LABEL, efforts) { effort ->
            onSelectionChange(selection.copy(effort = effort.ifEmpty { null }))
        }
    }
}

@Composable
private fun FavoriteChips(selection: ModelSelection, favorites: List<ModelSelection>, onSelect: (ModelSelection) -> Unit) {
    Text("Favourites", style = MaterialTheme.typography.labelLarge)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        favorites.forEach { favorite ->
            FilterChip(selected = favorite == selection, onClick = { onSelect(favorite) }, label = { Text(favorite.label) })
        }
    }
}

/** A read-only text field that opens a menu. [options] holds pairs of value and label. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, displayValue: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { isExpanded = it }) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(text = { Text(optionLabel) }, onClick = { isExpanded = false; onSelect(value) })
            }
        }
    }
}
