package build.conductor.android.client.data

import build.conductor.android.client.data.api.FavoriteModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ModelCatalogTest {
    private val createSessionSchema: JsonObject by lazy {
        val spec = Json.parseToJsonElement(File("../docs/openapi.json").readText()).jsonObject
        spec.path("paths", "/v0/sessions", "post", "requestBody", "content", "application/json", "schema", "properties")
    }

    @Test
    fun `the catalogue has every model id that the OpenAPI spec accepts`() {
        val specModels = enumValues(createSessionSchema.getValue("model")).toSet()

        assertEquals(specModels, ModelCatalog.agents.flatMap { it.models }.toSet())
    }

    @Test
    fun `the catalogue efforts are the efforts that the OpenAPI spec accepts`() {
        val specEfforts = enumValues(createSessionSchema.getValue("effort")).toSet()

        assertEquals(specEfforts, ModelCatalog.agents.flatMap { it.efforts }.toSet())
    }

    @Test
    fun `every catalogue agent is an agent in the OpenAPI spec`() {
        val specAgents = enumValues(createSessionSchema.getValue("agent")).toSet()

        assertEquals(specAgents - "acp", ModelCatalog.agents.map { it.id }.toSet())
    }

    @Test
    fun `the default selection is the first favourite that the API accepts`() {
        val favorites = listOf(FavoriteModel("acp", "custom"), FavoriteModel("codex", "gpt-6.1-sol", "high"))

        assertEquals(ModelSelection("codex", "gpt-6.1-sol", "high"), ModelCatalog.defaultSelection(favorites))
        assertEquals(ModelSelection("claude", "opus-5-1m", null), ModelCatalog.defaultSelection(emptyList()))
    }

    private fun enumValues(schema: JsonElement): List<String> =
        (schema.jsonObject["anyOf"] as JsonArray).map { it.jsonObject.getValue("enum").jsonArray.single().jsonPrimitive.content }

    private fun JsonObject.path(vararg keys: String): JsonObject = keys.fold(this) { node, key -> node.getValue(key).jsonObject }
}
