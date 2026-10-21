package build.conductor.android.client.ui.components

import android.content.ClipData
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import build.conductor.android.client.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Copies [text] to the clipboard and shows a tick for a short time, because Android before 13 shows no copy notice. */
@Composable
fun CopyButton(text: String, contentDescription: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(COPIED_TICK_MILLIS)
            isCopied = false
        }
    }
    IconButton(
        onClick = {
            coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(contentDescription, text))) }
            isCopied = true
        },
        modifier = modifier,
    ) {
        val tint = LocalContentColor.current.copy(alpha = ICON_ALPHA)
        if (isCopied) {
            Icon(Icons.Filled.Check, contentDescription = "Copied", tint = tint, modifier = Modifier.size(ICON_SIZE))
        } else {
            Icon(painterResource(R.drawable.ic_content_copy), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(ICON_SIZE))
        }
    }
}

private const val COPIED_TICK_MILLIS = 2_000L
private const val ICON_ALPHA = 0.7f
private val ICON_SIZE = 18.dp
