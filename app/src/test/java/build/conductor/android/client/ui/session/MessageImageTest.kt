package build.conductor.android.client.ui.session

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import build.conductor.android.client.ComponentActivityRule
import build.conductor.android.client.data.transcript.TranscriptItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MessageImageTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRule()).around(composeRule)

    @Test
    fun `a picture from a file on the cloud machine shows a notice with its name`() {
        showMessage("The tabs:\n\n![Workspace tabs](</home/vercel-sandbox/app/.context/workspace-tabs.png>)\n\nDone.")

        composeRule.waitUntil(WAIT_MILLIS) { composeRule.onAllNodesWithTag(IMAGE_NOTICE_TAG).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Workspace tabs").assertExists()
        composeRule.onNodeWithText("workspace-tabs.png").assertExists()
        composeRule.onNodeWithText(WORKSPACE_FILE_NOTICE).assertExists()
        composeRule.onNodeWithText("Done.").assertExists()
    }

    @Test
    fun `picture markdown inside a code block stays code`() {
        showMessage("```markdown\n![Workspace tabs](/tmp/tabs.png)\n```")

        composeRule.waitUntil(WAIT_MILLIS) { composeRule.onAllNodesWithTag(CODE_BLOCK_TAG).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(IMAGE_NOTICE_TAG).assertDoesNotExist()
    }

    @Test
    fun `only http and https addresses load from the web`() {
        assertTrue(isWebAddress("https://github.com/x/y.png"))
        assertTrue(isWebAddress("http://example.com/y.png"))
        assertFalse(isWebAddress("/home/vercel-sandbox/app/.context/tabs.png"))
        assertFalse(isWebAddress("file:///tmp/tabs.png"))
    }

    private fun showMessage(markdown: String) = composeRule.setContent { TranscriptRow(TranscriptItem.AssistantText("a1", markdown)) }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
