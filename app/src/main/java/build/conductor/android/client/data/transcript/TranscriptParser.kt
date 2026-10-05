package build.conductor.android.client.data.transcript

import build.conductor.android.client.data.api.Message
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** One row in the chat view. [key] is stable across polls, so a list can use it as an item key. */
sealed interface TranscriptItem {
    val key: String

    data class UserPrompt(
        override val key: String,
        val text: String,
        val delivery: Delivery,
        val clientMessageId: String?,
    ) : TranscriptItem

    data class AssistantText(override val key: String, val markdown: String) : TranscriptItem

    data class ToolCall(override val key: String, val toolName: String, val summary: String) : TranscriptItem

    data class ToolResult(override val key: String, val output: String, val isError: Boolean) : TranscriptItem

    data class TurnEnd(
        override val key: String,
        val isError: Boolean,
        val durationMillis: Long?,
        val costUsd: Double?,
    ) : TranscriptItem

    data class Notice(override val key: String, val text: String) : TranscriptItem
}

enum class Delivery { SENDING, QUEUED, SENT }

/** Converts API messages into chat rows. Agent housekeeping events produce no rows. */
object TranscriptParser {
    fun parse(messages: List<Message>): List<TranscriptItem> = messages.flatMap(::parse)

    fun parse(message: Message): List<TranscriptItem> {
        val content = message.content as? JsonObject ?: return emptyList()
        return when (message.type) {
            USER_MESSAGE -> listOfNotNull(parseUserMessage(message.id, content))
            AGENT -> parseAgentEvent(message.id, content.obj("rawPayload"))
            else -> emptyList()
        }
    }

    private fun parseUserMessage(key: String, content: JsonObject): TranscriptItem.UserPrompt? {
        val text = content.string("message") ?: return null
        val delivery = if (content.string("state") == "queued") Delivery.QUEUED else Delivery.SENT
        return TranscriptItem.UserPrompt(key, text, delivery, content.string("id"))
    }

    private fun parseAgentEvent(key: String, payload: JsonObject?): List<TranscriptItem> {
        // Events with a parent tool call come from a subagent; the chat shows only the main thread.
        if (payload == null || payload.string("parent_tool_use_id") != null) return emptyList()
        return when (payload.string("type")) {
            "assistant" -> parseBlocks(key, payload.obj("message")?.get("content"), ::parseAssistantBlock)
            "user" -> parseBlocks(key, payload.obj("message")?.get("content"), ::parseUserBlock)
            "result" -> listOf(parseResult(key, payload))
            in HIDDEN_EVENT_TYPES -> emptyList()
            else -> listOfNotNull(parseUnknownEvent(key, payload))
        }
    }

    private fun parseBlocks(
        key: String,
        blocks: JsonElement?,
        parseBlock: (String, JsonObject) -> TranscriptItem?,
    ): List<TranscriptItem> = when (blocks) {
        is JsonArray -> blocks.mapIndexedNotNull { index, block -> (block as? JsonObject)?.let { parseBlock("$key#$index", it) } }
        is JsonPrimitive -> listOfNotNull(blocks.contentOrNull()?.takeIf { it.isNotBlank() }?.let { TranscriptItem.Notice(key, it) })
        else -> emptyList()
    }

    private fun parseAssistantBlock(key: String, block: JsonObject): TranscriptItem? = when (block.string("type")) {
        "text" -> block.string("text")?.takeIf { it.isNotBlank() }?.let { TranscriptItem.AssistantText(key, it) }
        "tool_use" -> TranscriptItem.ToolCall(key, block.string("name") ?: "Tool", summarizeToolInput(block.obj("input")))
        else -> null
    }

    private fun parseUserBlock(key: String, block: JsonObject): TranscriptItem? = when (block.string("type")) {
        "tool_result" -> TranscriptItem.ToolResult(key, textOf(block["content"]).take(MAX_TOOL_OUTPUT), block.boolean("is_error") == true)
        "text" -> block.string("text")?.takeIf { it.isNotBlank() }?.let { TranscriptItem.Notice(key, it) }
        else -> null
    }

    private fun parseResult(key: String, payload: JsonObject) = TranscriptItem.TurnEnd(
        key = key,
        isError = payload.boolean("is_error") == true,
        durationMillis = (payload["duration_ms"] as? JsonPrimitive)?.longOrNull,
        costUsd = (payload["total_cost_usd"] as? JsonPrimitive)?.doubleOrNull,
    )

    private fun parseUnknownEvent(key: String, payload: JsonObject): TranscriptItem? =
        (payload.string("text") ?: payload.string("message"))?.takeIf { it.isNotBlank() }?.let { TranscriptItem.Notice(key, it) }

    /** The most readable field of a tool input, on one line. */
    fun summarizeToolInput(input: JsonObject?): String {
        val value = TOOL_SUMMARY_KEYS.firstNotNullOfOrNull { input?.string(it)?.takeIf(String::isNotBlank) } ?: return ""
        return value.lineSequence().first().take(MAX_SUMMARY)
    }

    private fun textOf(element: JsonElement?): String = when (element) {
        is JsonPrimitive -> element.contentOrNull().orEmpty()
        is JsonArray -> element.mapNotNull { (it as? JsonObject)?.string("text") }.joinToString("\n")
        else -> ""
    }

    private const val USER_MESSAGE = "userMessage"
    private const val AGENT = "agent"
    private const val MAX_SUMMARY = 160
    private const val MAX_TOOL_OUTPUT = 4_000
    private val HIDDEN_EVENT_TYPES = setOf("system", "rate_limit_event", "tool_progress", "command_lifecycle", "stream_event")
    private val TOOL_SUMMARY_KEYS = listOf("description", "command", "file_path", "path", "pattern", "url", "query", "prompt", "subject")
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull()

private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else null
