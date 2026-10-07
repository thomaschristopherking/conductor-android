package build.conductor.android.client.ui.session

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import build.conductor.android.client.ComponentActivityRule
import build.conductor.android.client.data.transcript.Delivery
import build.conductor.android.client.data.transcript.TranscriptItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TranscriptScrollTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRule()).around(composeRule)

    private var items by mutableStateOf(prompts(count = 40))
    private lateinit var scrollState: TranscriptScrollState

    private fun showTranscript() {
        composeRule.setContent {
            scrollState = rememberTranscriptScrollState()
            Transcript(items, scrollState)
        }
    }

    @Test
    fun `a long transcript opens at the newest row`() {
        showTranscript()

        composeRule.onNodeWithText("Prompt 39").assertIsDisplayed()
        composeRule.onNodeWithText("Prompt 0").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(JUMP_BUTTON).assertDoesNotExist()
    }

    @Test
    fun `the end of a newest row that is taller than the screen is visible`() {
        items = prompts(count = 5) + prompt(index = 5, text = List(80) { "Line $it" }.joinToString("\n"))
        showTranscript()

        val listBottom = composeRule.onNode(hasScrollToIndexAction()).getUnclippedBoundsInRoot().bottom
        val rowBottom = composeRule.onNodeWithText("Line 79", substring = true).getUnclippedBoundsInRoot().bottom
        assertEquals((listBottom - LIST_PADDING - ROW_PADDING).value, rowBottom.value, 1f)
    }

    @Test
    fun `a new row appears when the transcript shows the newest row`() {
        showTranscript()

        items = items + prompt(index = 40)

        composeRule.onNodeWithText("Prompt 40").assertIsDisplayed()
    }

    @Test
    fun `scrolling back to older rows shows the jump button`() {
        showTranscript()

        scrollToOlderRows()

        composeRule.onNodeWithText("Prompt 39").assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription(JUMP_BUTTON).assertIsDisplayed()
    }

    @Test
    fun `a new row does not move a transcript that the user scrolled back`() {
        showTranscript()
        scrollToOlderRows()

        items = items + prompt(index = 40)

        composeRule.onNodeWithText("Prompt 40").assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription(JUMP_BUTTON).assertIsDisplayed()
    }

    @Test
    fun `the jump button scrolls to the newest row and then hides`() {
        showTranscript()
        scrollToOlderRows()

        composeRule.onNodeWithContentDescription(JUMP_BUTTON).performClick()

        composeRule.onNodeWithText("Prompt 39").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(JUMP_BUTTON).assertDoesNotExist()
    }

    @Test
    fun `a sent prompt scrolls a transcript that the user scrolled back`() {
        showTranscript()
        scrollToOlderRows()

        scrollState.followNewest()
        items = items + prompt(index = 40)

        composeRule.onNodeWithText("Prompt 40").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(JUMP_BUTTON).assertDoesNotExist()
    }

    private fun scrollToOlderRows() {
        repeat(3) { composeRule.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() } }
        composeRule.waitForIdle()
    }

    private companion object {
        const val JUMP_BUTTON = "Scroll to the newest message"
        val LIST_PADDING = 16.dp
        val ROW_PADDING = 12.dp

        fun prompts(count: Int): List<TranscriptItem> = List(count) { prompt(it) }

        fun prompt(index: Int, text: String = "Prompt $index"): TranscriptItem =
            TranscriptItem.UserPrompt(key = "prompt-$index", text = text, delivery = Delivery.SENT, clientMessageId = null)
    }
}
