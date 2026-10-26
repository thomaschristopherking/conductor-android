package build.conductor.android.client.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import build.conductor.android.client.update.UpdateState
import build.conductor.android.client.update.newerRelease

@Composable
internal fun UpdateSection(viewModel: AppUpdateViewModel) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    Text("App update", style = MaterialTheme.typography.titleMedium)
    Text("Installed: build ${viewModel.installedBuild}", style = MaterialTheme.typography.bodyMedium)
    Text(updateMessage(state), style = MaterialTheme.typography.bodyMedium)
    (state as? UpdateState.Downloading)?.let { DownloadProgress(it.fraction) }
    UpdateButtons(state, viewModel)
}

private fun updateMessage(state: UpdateState): String = when (state) {
    UpdateState.NotChecked -> "The app looks for a new build on GitHub when it opens."
    UpdateState.Checking -> "Looking for a new build…"
    UpdateState.UpToDate -> "This is the newest build."
    is UpdateState.Available -> "Build ${state.release.buildNumber} is ready to install."
    is UpdateState.Downloading -> "Downloading build ${state.release.buildNumber}…"
    is UpdateState.NeedsPermission -> "Allow Conductor to install apps in the Android settings, then come back and tap the button again."
    is UpdateState.Installing -> "Confirm the update in the Android dialog. The app restarts on the new build."
    is UpdateState.Failed -> state.message
}

@Composable
private fun DownloadProgress(fraction: Float?) {
    if (fraction == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun UpdateButtons(state: UpdateState, viewModel: AppUpdateViewModel) {
    val release = state.newerRelease
    val isBusy = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (release != null) Button(onClick = viewModel::update, enabled = !isBusy) { Text("Update to build ${release.buildNumber}") }
        OutlinedButton(onClick = viewModel::check, enabled = !isBusy) { Text("Check again") }
        if (state is UpdateState.Checking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}
