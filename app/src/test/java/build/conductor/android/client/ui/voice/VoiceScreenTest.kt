package build.conductor.android.client.ui.voice

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import build.conductor.android.client.ComponentActivityRule
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.voice.VoicePhase
import build.conductor.android.client.voice.VoiceState
import build.conductor.android.client.voice.VoiceTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VoiceScreenTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRule()).around(composeRule)

    private val calls = mutableListOf<String>()
    private val actions = VoiceActions(
        onTap = { calls += "tap" },
        onStopAgent = { calls += "stop" },
        onStart = { calls += "start" },
        onEnd = { calls += "end" },
        onBack = { calls += "back" },
    )

    @Test
    fun `while the app speaks, the screen shows the sentence, and a tap anywhere interrupts`() {
        val state = VoiceState(TARGET, VoicePhase.SPEAKING, AgentStatus.WORKING, caption = "I fixed the test.")
        composeRule.setContent { VoiceContent(TARGET, state, problem = null, actions) }

        composeRule.onNodeWithText("Speaking").assertExists()
        composeRule.onNodeWithText("I fixed the test.").assertExists()
        composeRule.onNodeWithContentDescription("Interrupt").performClick()
        composeRule.onNodeWithText("Stop agent").assertIsEnabled().performClick()

        assertEquals(listOf("tap", "stop"), calls)
    }

    @Test
    fun `stop agent is off while the agent waits, and end voice ends voice mode`() {
        val state = VoiceState(TARGET, VoicePhase.LISTENING, AgentStatus.IDLE)
        composeRule.setContent { VoiceContent(TARGET, state, problem = null, actions) }

        composeRule.onNodeWithText("The agent is waiting for you.").assertExists()
        composeRule.onNodeWithText("Stop agent").assertIsNotEnabled()
        composeRule.onNodeWithText("End voice").performClick()

        assertEquals(listOf("end"), calls)
    }

    @Test
    fun `when voice mode is off, the screen shows why and can start it again`() {
        composeRule.setContent { VoiceContent(TARGET, state = null, problem = "Voice mode needs the microphone permission.", actions) }

        composeRule.onNodeWithText("Voice mode needs the microphone permission.").assertExists()
        composeRule.onNodeWithText("Start voice").performClick()

        assertEquals(listOf("start"), calls)
    }

    private companion object {
        val TARGET = VoiceTarget("w1", "s1", "Fix CI")
    }
}
