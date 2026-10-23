package build.conductor.android.client.data.transcript

import build.conductor.android.client.Fixtures
import build.conductor.android.client.data.api.Message
import build.conductor.android.client.data.api.Page
import build.conductor.android.client.data.api.conductorJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentSetupTest {
    private fun fixtureMessages(name: String): List<Message> =
        conductorJson.decodeFromString(Page.serializer(Message.serializer()), Fixtures.read(name)).data

    private var nextId = 0

    private fun message(type: String, content: String) = Message("m${nextId++}", "s1", nextId.toLong(), type, Json.parseToJsonElement(content), "2026-10-23T12:00:00Z")

    private fun prompt(text: String) = message("userMessage", """{"type":"userMessage","message":"$text","state":"sent"}""")

    private fun init(model: String, extra: String = "") =
        message("agent", """{"type":"agent","rawPayload":{"type":"system","subtype":"init","model":"$model","slash_commands":["compact","review"]$extra}}""")

    private fun turnEnd() = message("agent", """{"type":"agent","rawPayload":{"type":"result","subtype":"success"}}""")

    @Test
    fun `the init event of a live transcript gives the slash commands and the model`() {
        val setup = AgentSetup().after(fixtureMessages("messages_all_kinds.json"))

        assertEquals(listOf("build-review-cycle", "build-review-cycle-auto", "build-review-cycle-claude-only"), setup.slashCommands)
        assertEquals("claude-opus-5-5[1m]", setup.model)
    }

    @Test
    fun `a model prompt outlasts the init event of its own turn, and the next init event replaces it`() {
        val beforeSwitch = AgentSetup().after(listOf(init("claude-sonnet-4-6"), turnEnd()))

        val inSwitchTurn = beforeSwitch.after(listOf(prompt("/model claude-haiku-4-5"), init("claude-sonnet-4-6"), turnEnd()))
        assertEquals("claude-haiku-4-5", inSwitchTurn.model)

        val nextTurn = inSwitchTurn.after(listOf(prompt("Go on"), init("claude-opus-5-5[1m]")))
        assertEquals("claude-opus-5-5[1m]", nextTurn.model)
    }

    @Test
    fun `an effort prompt sets the effort, and a prompt that only starts with a command changes nothing`() {
        val setup = AgentSetup().after(listOf(prompt("/effort max"), prompt("/model"), prompt("/model one two")))

        assertEquals("max", setup.effort)
        assertNull(setup.model)
    }

    @Test
    fun `the init event of a subagent changes nothing`() {
        val setup = AgentSetup().after(listOf(init("claude-opus-5-5[1m]"), init("claude-haiku-4-5", extra = ""","parent_tool_use_id":"t1"""")))

        assertEquals("claude-opus-5-5[1m]", setup.model)
    }

    @Test
    fun `a prompt that waits for delivery counts`() {
        val setup = AgentSetup().after(listOf(init("claude-sonnet-4-6"))).afterPrompts(listOf("/model claude-fable-5-1"))

        assertEquals("claude-fable-5-1", setup.model)
        assertEquals(listOf("compact", "review"), setup.slashCommands)
    }
}
