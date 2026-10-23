package build.conductor.android.client.ui.session

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import build.conductor.android.client.ComponentActivityRule
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.ModelSelection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ComposerTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRule()).around(composeRule)

    private var state by mutableStateOf(
        SessionUiState(
            title = "Session",
            status = AgentStatus.IDLE,
            slashCommands = listOf("compact", "code-review", "review"),
            modelSelection = ModelSelection("claude", "sonnet-4-6", null),
        ),
    )
    private val switchedModels = mutableListOf<String>()

    private val actions = ComposerActions(
        onDraftChange = { state = state.copy(draft = it) },
        onChooseCommand = { state = state.copy(draft = completeSlashCommand(it)) },
        onSwitchModel = { switchedModels += it },
        onSwitchEffort = {},
        onSend = {},
    )

    @Test
    fun `a chosen command fills the draft, closes the list and puts the cursor at the end`() {
        composeRule.setContent { Composer(state, actions) }

        composeRule.onNode(hasSetTextAction()).performTextInput("/co")
        composeRule.onNodeWithTag(SLASH_COMMAND_LIST_TAG).assertExists()
        composeRule.onNodeWithText("/compact").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("now")

        assertEquals("/compact now", state.draft)
        composeRule.onNodeWithTag(SLASH_COMMAND_LIST_TAG).assertDoesNotExist()
    }

    @Test
    fun `the model menu switches the model`() {
        composeRule.setContent { Composer(state, actions) }

        composeRule.onNodeWithContentDescription("Model: sonnet-4-6").performClick()
        composeRule.onNodeWithText("opus-5-5-1m").performClick()

        assertEquals(listOf("opus-5-5-1m"), switchedModels)
    }

    @Test
    fun `the model buttons are off while the agent works`() {
        state = state.copy(status = AgentStatus.WORKING)
        composeRule.setContent { Composer(state, actions) }

        composeRule.onNodeWithContentDescription("Model: sonnet-4-6").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Effort: Default effort").assertIsNotEnabled()
        state = state.copy(status = AgentStatus.IDLE)
        composeRule.onNodeWithContentDescription("Model: sonnet-4-6").assertIsEnabled()
    }
}
