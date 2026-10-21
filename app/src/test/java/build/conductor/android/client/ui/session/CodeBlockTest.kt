package build.conductor.android.client.ui.session

import android.app.Application
import android.content.ClipboardManager
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.robolectric.RuntimeEnvironment
import build.conductor.android.client.ComponentActivityRule
import build.conductor.android.client.data.transcript.TranscriptItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CodeBlockTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRule()).around(composeRule)

    private val longLine = List(60) { "word$it" }.joinToString(" ")

    @Test
    fun `a message draws its code block in the first frame`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { TranscriptRow(TranscriptItem.AssistantText("a1", "```bash\n./gradlew test\n```")) }

        composeRule.onNodeWithTag(CODE_BLOCK_TAG).assertExists()
    }

    @Test
    fun `a long code line wraps inside the screen`() {
        composeRule.setContent { TranscriptRow(TranscriptItem.AssistantText("a1", "```kotlin\n$longLine\n```")) }

        val rootWidth = composeRule.onRoot().getUnclippedBoundsInRoot().right
        val block = composeRule.onNodeWithTag(CODE_BLOCK_TAG).getUnclippedBoundsInRoot()
        assertTrue("The code block is wider than the screen", block.right <= rootWidth)
        assertTrue("The long line did not wrap", block.bottom - block.top > 80.dp)
    }

    @Test
    fun `the copy button copies only the code`() {
        composeRule.setContent { TranscriptRow(TranscriptItem.AssistantText("a1", "Run this:\n\n```bash\n./gradlew test\n```")) }

        composeRule.onNodeWithContentDescription("Copy code").performClick()
        composeRule.waitForIdle()

        assertEquals("./gradlew test", clipboardText())
    }

    @Test
    fun `the message copy button copies the whole message`() {
        val markdown = "Run this:\n\n```bash\n./gradlew test\n```"
        composeRule.setContent { TranscriptRow(TranscriptItem.AssistantText("a1", markdown)) }

        composeRule.onNodeWithContentDescription("Copy message").performClick()
        composeRule.waitForIdle()

        assertEquals(markdown, clipboardText())
    }

    private fun clipboardText(): String {
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        return clipboard.primaryClip!!.getItemAt(0).text.toString()
    }
}
