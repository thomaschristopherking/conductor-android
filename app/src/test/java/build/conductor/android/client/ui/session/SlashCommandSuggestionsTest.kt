package build.conductor.android.client.ui.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SlashCommandSuggestionsTest {
    private val commands = listOf("review", "compact", "superpowers:brainstorming", "code-review", "context")

    @Test
    fun `a slash alone lists every command in alphabetical order`() {
        assertEquals(listOf("code-review", "compact", "context", "review", "superpowers:brainstorming"), suggestSlashCommands("/", commands))
    }

    @Test
    fun `names that start with the typed text come before names that contain it`() {
        assertEquals(listOf("review", "code-review"), suggestSlashCommands("/rev", commands))
        assertEquals(listOf("superpowers:brainstorming"), suggestSlashCommands("/BRAIN", commands))
    }

    @Test
    fun `nothing shows without a leading slash, after a space, or with no match`() {
        assertEquals(emptyList<String>(), suggestSlashCommands("compact", commands))
        assertEquals(emptyList<String>(), suggestSlashCommands("/compact now", commands))
        assertEquals(emptyList<String>(), suggestSlashCommands("/zzz", commands))
    }

    @Test
    fun `a chosen command ends with a space, so the list closes`() {
        assertEquals("/compact ", completeSlashCommand("compact"))
        assertEquals(emptyList<String>(), suggestSlashCommands(completeSlashCommand("compact"), commands))
    }
}
