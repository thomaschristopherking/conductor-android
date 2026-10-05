package build.conductor.android.client.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.conductor.android.client.data.ModelSelection
import build.conductor.android.client.ui.components.ModelPicker

@Composable
fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename workspace") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ArchiveDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archive \"$name\"?") },
        text = { Text("Conductor stops the workspace and hides it. You can restore it later in the Conductor app.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Archive") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NewSessionDialog(
    initialSelection: ModelSelection,
    favorites: List<ModelSelection>,
    onConfirm: (NewSessionInput) -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by remember(initialSelection) { mutableStateOf(initialSelection) }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New session") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelPicker(selection, favorites, onSelectionChange = { selection = it })
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(message, { message = it }, label = { Text("First prompt (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(NewSessionInput(selection, name, message)) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
