package build.conductor.android.client.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextTest {
    @Test
    fun `markdown marks go, and each sentence is one piece`() {
        val markdown = "## Summary\n\nI **fixed** the `parser`. It now _reads_ empty files!\n\n- First point\n- [ ] Open task"

        assertEquals(
            listOf("Summary", "I fixed the parser.", "It now _reads_ empty files!", "First point", "Open task"),
            SpeechText.sentences(markdown),
        )
    }

    @Test
    fun `a code block and a table each become one notice`() {
        val markdown = "Run this:\n```bash\n./gradlew test\n```\n| File | Lines |\n| --- | --- |\n| a.kt | 3 |\nDone."

        assertEquals(listOf("Run this:", SpeechText.CODE_NOTICE, SpeechText.TABLE_NOTICE, "Done."), SpeechText.sentences(markdown))
    }

    @Test
    fun `links read as their text, bare addresses as a link, and paths as their file name`() {
        val markdown = "See [the docs](https://example.com) or https://example.com/x. I changed app/src/main/Foo.kt and ui/Bar.kt, and/or more."

        assertEquals(listOf("See the docs or a link.", "I changed Foo.kt and Bar.kt, and/or more."), SpeechText.sentences(markdown))
    }

    @Test
    fun `a long sentence splits at a comma or a space`() {
        val sentence = (1..80).joinToString(", ") { "item $it" } + "."

        val pieces = SpeechText.sentences(sentence)

        assertEquals(sentence, pieces.joinToString(" "))
        assert(pieces.all { it.length <= 281 }) { pieces }
    }

    @Test
    fun `an unclosed code block at the end still becomes a notice`() {
        assertEquals(listOf("Here:", SpeechText.CODE_NOTICE), SpeechText.sentences("Here:\n```kotlin\nval x = 1"))
    }
}
