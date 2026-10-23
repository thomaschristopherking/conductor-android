package build.conductor.android.client.data

import build.conductor.android.client.data.transcript.AgentSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaudeModelSwitchTest {
    private val base = ModelSelection("claude", "sonnet-4-6", "high")

    @Test
    fun `a model command uses the Claude Code name, which marks a long context with 1m in brackets`() {
        assertEquals("/model claude-opus-5-5[1m]", ClaudeModelSwitch.modelCommand("opus-5-5-1m"))
        assertEquals("/model claude-fable-5-1", ClaudeModelSwitch.modelCommand("fable-5-1"))
    }

    @Test
    fun `an effort command sends auto for the agent default`() {
        assertEquals("/effort xhigh", ClaudeModelSwitch.effortCommand("xhigh"))
        assertEquals("/effort auto", ClaudeModelSwitch.effortCommand(null))
    }

    @Test
    fun `only a session with a Claude model gets a selection`() {
        assertEquals(base, ClaudeModelSwitch.sessionSelection("sonnet-4-6", "high"))
        assertNull(ClaudeModelSwitch.sessionSelection("gpt-6.1-sol", "high"))
        assertNull(ClaudeModelSwitch.sessionSelection(null, null))
    }

    @Test
    fun `the transcript model and effort replace the session values`() {
        val current = ClaudeModelSwitch.currentSelection(base, AgentSetup(model = "claude-opus-5-5[1m]", effort = "max"))

        assertEquals(ModelSelection("claude", "opus-5-5-1m", "max"), current)
    }

    @Test
    fun `an alias or an unknown name keeps the session model, and auto clears the effort`() {
        val current = ClaudeModelSwitch.currentSelection(base, AgentSetup(model = "opus", effort = "auto"))

        assertEquals(ModelSelection("claude", "sonnet-4-6", null), current)
    }

    @Test
    fun `every catalog model goes to a command and back to the same model`() {
        ClaudeModelSwitch.models.forEach { model ->
            val name = ClaudeModelSwitch.modelCommand(model).removePrefix("/model ")
            assertEquals(model, ClaudeModelSwitch.currentSelection(base, AgentSetup(model = name)).model)
        }
    }
}
