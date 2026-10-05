package build.conductor.android.client.data.transcript

import build.conductor.android.client.Fixtures
import build.conductor.android.client.data.api.Message
import build.conductor.android.client.data.api.Page
import build.conductor.android.client.data.api.conductorJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptParserTest {
    private fun fixtureMessages(name: String): List<Message> =
        conductorJson.decodeFromString(Page.serializer(Message.serializer()), Fixtures.read(name)).data

    @Test
    fun `every kind of live message parses, and housekeeping events produce no rows`() {
        val messages = fixtureMessages("messages_all_kinds.json")

        val items = TranscriptParser.parse(messages)

        assertTrue(items.any { it is TranscriptItem.UserPrompt })
        assertTrue(items.any { it is TranscriptItem.AssistantText })
        assertTrue(items.any { it is TranscriptItem.ToolCall })
        assertTrue(items.any { it is TranscriptItem.ToolResult })
        assertTrue(items.any { it is TranscriptItem.TurnEnd })
        val systemMessages = messages.filter { it.content.toString().contains("\"type\":\"system\"") }
        assertTrue(systemMessages.isNotEmpty())
        assertTrue(systemMessages.all { TranscriptParser.parse(it).isEmpty() })
    }

    @Test
    fun `item keys are unique`() {
        val items = TranscriptParser.parse(fixtureMessages("messages_all_kinds.json") + fixtureMessages("messages_page.json"))

        assertEquals(items.size, items.map { it.key }.toSet().size)
    }

    @Test
    fun `a user message keeps its text, delivery state and client id`() {
        val prompt = TranscriptParser.parse(message("userMessage", """{"type":"userMessage","id":"c1","message":"Run the tests","state":"queued"}"""))
            .single() as TranscriptItem.UserPrompt

        assertEquals("Run the tests", prompt.text)
        assertEquals(Delivery.QUEUED, prompt.delivery)
        assertEquals("c1", prompt.clientMessageId)
    }

    @Test
    fun `assistant text is markdown and thinking is hidden`() {
        val items = TranscriptParser.parse(
            agent("""{"type":"assistant","message":{"content":[{"type":"thinking","thinking":"hmm"},{"type":"text","text":"**Done**"}]}}"""),
        )

        assertEquals(listOf(TranscriptItem.AssistantText("m1#1", "**Done**")), items)
    }

    @Test
    fun `a tool call shows its description, or its command when it has no description`() {
        val described = agent("""{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Bash","input":{"command":"ls","description":"List files"}}]}}""")
        val undescribed = agent("""{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Bash","input":{"command":"ls -la\nmore"}}]}}""")

        assertEquals("List files", (TranscriptParser.parse(described).single() as TranscriptItem.ToolCall).summary)
        assertEquals("ls -la", (TranscriptParser.parse(undescribed).single() as TranscriptItem.ToolCall).summary)
    }

    @Test
    fun `a tool result accepts text content or a list of text blocks`() {
        val plain = agent("""{"type":"user","message":{"content":[{"type":"tool_result","content":"ok","is_error":true}]}}""")
        val blocks = agent("""{"type":"user","message":{"content":[{"type":"tool_result","content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]}]}}""")

        val plainResult = TranscriptParser.parse(plain).single() as TranscriptItem.ToolResult
        assertEquals("ok", plainResult.output)
        assertTrue(plainResult.isError)
        assertEquals("a\nb", (TranscriptParser.parse(blocks).single() as TranscriptItem.ToolResult).output)
    }

    @Test
    fun `a result event ends the turn with duration and cost`() {
        val end = TranscriptParser.parse(agent("""{"type":"result","subtype":"success","is_error":false,"duration_ms":90734,"total_cost_usd":0.56}"""))
            .single() as TranscriptItem.TurnEnd

        assertFalse(end.isError)
        assertEquals(90_734L, end.durationMillis)
        assertEquals(0.56, end.costUsd!!, 0.0001)
    }

    @Test
    fun `subagent events are hidden`() {
        val items = TranscriptParser.parse(
            agent("""{"type":"assistant","parent_tool_use_id":"toolu_1","message":{"content":[{"type":"text","text":"inner"}]}}"""),
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `an unknown event with text becomes a notice and one without text is hidden`() {
        assertEquals(listOf(TranscriptItem.Notice("m1", "Codex says hi")), TranscriptParser.parse(agent("""{"type":"codex_event","text":"Codex says hi"}""")))
        assertTrue(TranscriptParser.parse(agent("""{"type":"codex_event","value":3}""")).isEmpty())
    }

    private fun agent(rawPayload: String) = message("agent", """{"type":"agent","rawPayload":$rawPayload}""")

    private fun message(type: String, content: String) = Message(
        id = "m1",
        sessionId = "s1",
        sessionIndex = 1,
        type = type,
        content = Json.parseToJsonElement(content) as JsonObject,
        receivedAt = "2026-10-05T06:00:00Z",
    )
}
