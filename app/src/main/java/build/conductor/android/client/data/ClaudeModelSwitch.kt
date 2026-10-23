package build.conductor.android.client.data

import build.conductor.android.client.data.transcript.AgentSetup

/**
 * Changes the model or effort of a running Claude session with a `/model` or `/effort` prompt.
 * The API takes no model on a message, and Claude Code keeps the change for the next turns of the session.
 */
object ClaudeModelSwitch {
    private val claude = requireNotNull(ModelCatalog.agent(CLAUDE_AGENT))

    val models: List<String> = claude.models
    val efforts: List<String> = claude.efforts

    /** The selection of a session whose model is a Claude model, or null for a session of a different agent. */
    fun sessionSelection(model: String?, effort: String?): ModelSelection? =
        model?.takeIf { it in models }?.let { ModelSelection(CLAUDE_AGENT, it, effort?.takeIf { level -> level in efforts }) }

    /** [base] with the model and effort that the transcript changed. */
    fun currentSelection(base: ModelSelection, setup: AgentSetup) = base.copy(
        model = setup.model?.let(::catalogModel) ?: base.model,
        effort = currentEffort(base.effort, setup.effort),
    )

    /** For example `/model claude-opus-5-5[1m]` for `opus-5-5-1m`. */
    fun modelCommand(model: String): String = "/model $CLAUDE_CODE_PREFIX${model.replace(LONG_CONTEXT_ID, LONG_CONTEXT_NAME)}"

    /** A null [effort] goes back to the agent default. */
    fun effortCommand(effort: String?): String = "/effort ${effort ?: DEFAULT_EFFORT}"

    /** Null for a name that is not a Claude Code name of a catalog model, such as the alias `opus`. */
    private fun catalogModel(claudeCodeName: String): String? =
        claudeCodeName.takeIf { it.startsWith(CLAUDE_CODE_PREFIX) }
            ?.removePrefix(CLAUDE_CODE_PREFIX)?.replace(LONG_CONTEXT_NAME, LONG_CONTEXT_ID)
            ?.takeIf { it in models }

    /** The agent default is a null effort, so an `/effort auto` prompt replaces the base effort with null. */
    private fun currentEffort(baseEffort: String?, argument: String?): String? = when (argument) {
        DEFAULT_EFFORT -> null
        in efforts -> argument
        else -> baseEffort
    }

    private const val CLAUDE_AGENT = "claude"
    private const val CLAUDE_CODE_PREFIX = "claude-"
    private const val LONG_CONTEXT_ID = "-1m"
    private const val LONG_CONTEXT_NAME = "[1m]"
    private const val DEFAULT_EFFORT = "auto"
}
