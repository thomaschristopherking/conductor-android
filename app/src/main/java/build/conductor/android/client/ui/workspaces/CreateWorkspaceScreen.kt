package build.conductor.android.client.ui.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.data.api.CreatedWorkspace
import build.conductor.android.client.ui.components.ModelPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorkspaceScreen(
    viewModel: CreateWorkspaceViewModel,
    projectName: String,
    onCreated: (CreatedWorkspace) -> Unit,
    onBack: () -> Unit,
) {
    val form by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.createdEvents.collect(onCreated) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New workspace") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Project: $projectName", style = MaterialTheme.typography.bodyMedium)
            OptionalField("Name", form.name, "Conductor chooses a name if you leave this empty.", viewModel::onNameChange)
            OptionalField("Branch", form.branch, "Leave empty to use the default branch.", viewModel::onBranchChange)
            ModelPicker(form.selection, form.favorites, viewModel::onSelectionChange)
            OutlinedTextField(
                value = form.message,
                onValueChange = viewModel::onMessageChange,
                label = { Text("First prompt (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::submit, enabled = !form.isSubmitting, modifier = Modifier.fillMaxWidth()) {
                if (form.isSubmitting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Create workspace")
            }
        }
    }
}

@Composable
private fun OptionalField(label: String, value: String, hint: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("$label (optional)") },
        supportingText = { Text(hint) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
