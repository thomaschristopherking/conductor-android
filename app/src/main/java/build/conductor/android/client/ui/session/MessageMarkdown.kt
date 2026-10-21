package build.conductor.android.client.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import build.conductor.android.client.ui.components.CopyButton
import com.mikepenz.markdown.compose.LocalReferenceLinkHandler
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.utils.resolveImageAlt
import com.mikepenz.markdown.utils.resolveImageLink
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode

/** Markdown whose code blocks wrap long lines and have a copy button, and whose pictures show as [MessageImage]. */
@Composable
fun MessageMarkdown(markdown: String, modifier: Modifier = Modifier) {
    Markdown(content = markdown, components = messageComponents(), imageTransformer = WebImageTransformer, modifier = modifier)
}

private fun messageComponents(): MarkdownComponents = markdownComponents(
    codeFence = { MarkdownCodeFence(it.content, it.node, it.typography.code) { code, language, style -> CodeBlock(code, language, style) } },
    codeBlock = { MarkdownCodeBlock(it.content, it.node, it.typography.code) { code, language, style -> CodeBlock(code, language, style) } },
    checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
    paragraph = { model ->
        val images = model.node.imagesOnly()
        if (images == null) MarkdownParagraph(model.content, model.node, style = model.typography.paragraph) else images.forEach { MessageImage(it, model.content) }
    },
    image = { model -> MessageImage(model.node, model.content) },
)

@Composable
private fun MessageImage(image: ASTNode, content: String) {
    val link = image.resolveImageLink(content, LocalReferenceLinkHandler.current) ?: return
    MessageImage(link.removeSurrounding("<", ">"), image.resolveImageAlt(content))
}

/** The pictures of a paragraph that holds nothing else, or null; such a paragraph shows each picture as a block. */
internal fun ASTNode.imagesOnly(): List<ASTNode>? {
    val parts = children.filterNot { it.type == MarkdownTokenTypes.EOL || it.type == MarkdownTokenTypes.WHITE_SPACE }
    return parts.takeIf { it.isNotEmpty() && it.all { part -> part.type == MarkdownElementTypes.IMAGE } }
}

@Composable
internal fun CodeBlock(code: String, language: String?, style: TextStyle) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(CODE_BLOCK_TAG),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                Text(
                    text = language.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                CopyButton(code, contentDescription = "Copy code")
            }
            Text(text = code, style = style, softWrap = true, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
        }
    }
}

internal const val CODE_BLOCK_TAG = "code_block"
