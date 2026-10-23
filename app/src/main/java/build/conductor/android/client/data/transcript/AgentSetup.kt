package build.conductor.android.client.data.transcript

import build.conductor.android.client.data.api.Message
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a Claude transcript tells about the agent: the commands that a prompt can start with `/`, and the model and effort. */
data class AgentSetup(
    val slashCommands: List<String> = emptyList(),
    /** The Claude Code name of the model, for example `claude-opus-5-5[1m]`. */
    val model: String? = null,
    /** The argument of the last `/effort` prompt; `auto` is the agent default. */
    val effort: String? = null,
    /** The `init` event of the turn that runs a `/model` prompt still names the earlier model. */
    val isModelSwitchTurn: Boolean = false,
) {
    /** Claude Code writes an `init` event at the start of each turn, and a `/model` or `/effort` prompt changes the next turns. */
    fun after(messages: List<Message>): AgentSetup = messages.fold(this) { setup, message -> setup.after(message) }

    fun afterPrompts(texts: List<String>): AgentSetup = texts.fold(this) { setup, text -> setup.afterPrompt(text) }

    private fun after(message: Message): AgentSetup {
        val content = message.content as? JsonObject ?: return this
        return when (message.type) {
            USER_MESSAGE -> content.string("message")?.let(::afterPrompt) ?: this
            AGENT -> content.obj("rawPayload")?.let(::afterAgentEvent) ?: this
            else -> this
        }
    }

    /** Events with a parent tool call come from a subagent, which has its own model. */
    private fun afterAgentEvent(payload: JsonObject): AgentSetup = when {
        payload.string("parent_tool_use_id") != null -> this
        payload.string("type") == "result" -> copy(isModelSwitchTurn = false)
        payload.string("type") == "system" && payload.string("subtype") == "init" -> afterInit(payload)
        else -> this
    }

    private fun afterInit(payload: JsonObject) = copy(
        slashCommands = slashCommandsOf(payload) ?: slashCommands,
        model = payload.string("model")?.takeUnless { isModelSwitchTurn } ?: model,
    )

    private fun afterPrompt(text: String): AgentSetup {
        val words = text.trim().split(WHITESPACE)
        if (words.size != 2) return this
        return when (words[0]) {
            MODEL_COMMAND -> copy(model = words[1], isModelSwitchTurn = true)
            EFFORT_COMMAND -> copy(effort = words[1])
            else -> this
        }
    }

    private companion object {
        const val USER_MESSAGE = "userMessage"
        const val AGENT = "agent"
        const val MODEL_COMMAND = "/model"
        const val EFFORT_COMMAND = "/effort"
        val WHITESPACE = Regex("\\s+")
    }
}

private fun slashCommandsOf(payload: JsonObject): List<String>? =
    (payload["slash_commands"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }?.distinct()
