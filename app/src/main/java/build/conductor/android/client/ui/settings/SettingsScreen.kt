package build.conductor.android.client.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val API_KEYS_URL = "https://app.conductor.build/home/api-keys"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    notice: String?,
    onKeySaved: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.eventFlow.collect { onKeySaved() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { if (onBack != null) BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (notice != null) NoticeCard(notice)
            SavedKeySection(state.savedKeyMask, state.signedInAs, viewModel::clearKey)
            KeyEntrySection(state, viewModel::onKeyInputChange, viewModel::testAndSave)
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
}

@Composable
private fun NoticeCard(notice: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(notice, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SavedKeySection(savedKeyMask: String?, signedInAs: String?, onClear: () -> Unit) {
    var isConfirmingClear by remember { mutableStateOf(false) }
    Text("API key", style = MaterialTheme.typography.titleMedium)
    Text(
        text = savedKeyMask?.let { "Saved key: $it" } ?: "No key saved. The app needs a Conductor API key.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (signedInAs != null) Text("Signed in as $signedInAs", style = MaterialTheme.typography.bodyMedium)
    if (savedKeyMask != null) OutlinedButton(onClick = { isConfirmingClear = true }) { Text("Clear key") }
    if (isConfirmingClear) {
        AlertDialog(
            onDismissRequest = { isConfirmingClear = false },
            title = { Text("Clear the API key?") },
            text = { Text("The app removes the key from this device. You must paste a key again to use the app.") },
            confirmButton = { TextButton(onClick = { isConfirmingClear = false; onClear() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { isConfirmingClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KeyEntrySection(state: SettingsUiState, onInputChange: (String) -> Unit, onSubmit: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    OutlinedTextField(
        value = state.keyInput,
        onValueChange = onInputChange,
        label = { Text(if (state.savedKeyMask == null) "Paste your API key" else "Replace the API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        isError = state.error != null,
        supportingText = { Text(state.error ?: "The app tests the key with a read-only call before it saves it.") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSubmit, enabled = state.keyInput.isNotBlank() && !state.isTesting) {
            if (state.isTesting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Test and save")
        }
        TextButton(onClick = { uriHandler.openUri(API_KEYS_URL) }) { Text("Get a key") }
    }
    Text(
        "The key is encrypted with a key that the Android Keystore holds. It never leaves this device except in requests to api.conductor.build.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
