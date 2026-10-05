package build.conductor.android.client.data

import build.conductor.android.client.data.api.FavoriteModel

/** The agents, models and effort levels that the OpenAPI spec in docs/openapi.json accepts. */
object ModelCatalog {
    val agents: List<Agent> = listOf(
        Agent(
            id = "claude",
            label = "Claude Code",
            models = listOf(
                "fable-5-1", "fable-5", "opus-5-5-1m", "opus-5-1m", "opus-4-8-1m", "opus-4-8", "opus-4-7-1m",
                "opus-4-7", "opus-4-6-1m", "sonnet-5-5-1m", "sonnet-5-1m", "sonnet-4-6-1m", "sonnet-4-6", "haiku-4-5",
            ),
            efforts = listOf("low", "medium", "high", "xhigh", "max"),
            defaultModel = "opus-5-1m",
        ),
        Agent(
            id = "codex",
            label = "Codex",
            models = listOf(
                "gpt-5.5", "gpt-5.4", "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna", "gpt-5.3-codex-spark",
                "gpt-5.3-codex", "gpt-5.2-codex", "gpt-6-astra", "gpt-6.1-sol", "gpt-6-sol", "gpt-6-luna",
                "gpt-daybreak-blue-latest",
            ),
            efforts = listOf("none", "low", "medium", "high", "xhigh", "max", "ultra"),
            defaultModel = "gpt-6.1-sol",
        ),
        Agent(
            id = "cursor",
            label = "Cursor",
            models = listOf("auto", "composer-2.5", "grok-4.7", "grok-4.6", "grok-4.5"),
            efforts = listOf("low", "medium", "high", "xhigh"),
            defaultModel = "composer-2.5",
        ),
    )

    fun agent(id: String): Agent? = agents.firstOrNull { it.id == id }

    fun isSupported(selection: ModelSelection): Boolean {
        val agent = agent(selection.agent) ?: return false
        return selection.model in agent.models && (selection.effort == null || selection.effort in agent.efforts)
    }

    /** The first favourite that the API accepts, or the Claude default. */
    fun defaultSelection(favorites: List<FavoriteModel>): ModelSelection =
        supportedFavorites(favorites).firstOrNull() ?: agents.first().let { ModelSelection(it.id, it.defaultModel, null) }

    fun supportedFavorites(favorites: List<FavoriteModel>): List<ModelSelection> =
        favorites.map { ModelSelection(it.agent, it.model, it.effort) }.filter(::isSupported).distinct()
}

data class Agent(
    val id: String,
    val label: String,
    val models: List<String>,
    val efforts: List<String>,
    val defaultModel: String,
)

/** A null [effort] tells the API to use the agent's default effort. */
data class ModelSelection(
    val agent: String,
    val model: String,
    val effort: String?,
) {
    val label: String
        get() = listOfNotNull(model, effort).joinToString(" · ")
}
