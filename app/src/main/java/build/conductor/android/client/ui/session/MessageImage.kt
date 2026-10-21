package build.conductor.android.client.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import build.conductor.android.client.R
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer

/** A picture in a message. The API cannot send a file from the cloud machine, so only a web address loads. */
@Composable
internal fun MessageImage(link: String, alt: String?) {
    if (isWebAddress(link)) WebImage(link, alt) else ImageNotice(link, alt, WORKSPACE_FILE_NOTICE)
}

internal fun isWebAddress(link: String): Boolean = link.startsWith("https://") || link.startsWith("http://")

/** A tap opens the full picture in the browser. */
@Composable
private fun WebImage(link: String, alt: String?) {
    val uriHandler = LocalUriHandler.current
    var hasFailed by remember(link) { mutableStateOf(false) }
    if (hasFailed) {
        ImageNotice(link, alt, FAILED_LOAD_NOTICE)
        return
    }
    AsyncImage(
        model = link,
        contentDescription = alt,
        contentScale = ContentScale.FillWidth,
        onError = { hasFailed = true },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { uriHandler.openUri(link) },
    )
}

@Composable
private fun ImageNotice(link: String, alt: String?, notice: String) {
    val fileName = link.substringAfterLast('/')
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(IMAGE_NOTICE_TAG),
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_image), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(alt ?: fileName, style = MaterialTheme.typography.titleSmall)
                Text(fileName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Loads a picture inside a line of text from a web address; a file path draws nothing there. */
internal object WebImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? = if (isWebAddress(link)) ImageData(rememberAsyncImagePainter(link)) else null
}

internal const val IMAGE_NOTICE_TAG = "image_notice"
internal const val WORKSPACE_FILE_NOTICE = "This picture is a file on the cloud machine. Open the workspace in the Conductor app on a computer to see it."
private const val FAILED_LOAD_NOTICE = "The picture did not load."
