package build.conductor.android.client.voice

import build.conductor.android.client.data.transcript.Question
import build.conductor.android.client.data.transcript.QuestionOption
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandsTest {
    @Test
    fun `a whole short phrase is a command, with fillers and punctuation ignored`() {
        assertEquals(Utterance.Command(VoiceCommand.STOP_AGENT), VoiceCommands.interpret("Stop."))
        assertEquals(Utterance.Command(VoiceCommand.REPEAT), VoiceCommands.interpret("OK, say that again please"))
        assertEquals(Utterance.Command(VoiceCommand.STATUS), VoiceCommands.interpret("What's the status?"))
        assertEquals(Utterance.Command(VoiceCommand.END_VOICE), VoiceCommands.interpret("Goodbye"))
        assertEquals(Utterance.Command(VoiceCommand.CANCEL), VoiceCommands.interpret("Never mind."))
    }

    @Test
    fun `a command word inside a longer sentence goes to the agent`() {
        assertEquals(Utterance.Words("Stop the build when a test fails"), VoiceCommands.interpret(" Stop the build when a test fails "))
    }

    @Test
    fun `a spoken option name or number chooses the option`() {
        val question = Question(null, "Which database?", listOf(QuestionOption("Postgres", null), QuestionOption("SQLite", null)), isMultiSelect = false)

        assertEquals(answers("Which database?", "SQLite"), SpokenQuestions.answer(listOf(question), "sqlite"))
        assertEquals(answers("Which database?", "SQLite"), SpokenQuestions.answer(listOf(question), "The second one"))
        assertEquals(answers("Which database?", "Postgres"), SpokenQuestions.answer(listOf(question), "option 1"))
    }

    @Test
    fun `a reply that names no option goes as free text`() {
        val question = Question(null, "Which database?", listOf(QuestionOption("Postgres", null), QuestionOption("SQLite", null)), isMultiSelect = false)

        assertEquals(answers("Which database?", "Use MySQL instead"), SpokenQuestions.answer(listOf(question), "Use MySQL instead"))
        assertEquals(answers("Which database?", "Postgres and SQLite"), SpokenQuestions.answer(listOf(question), "Postgres and SQLite"))
    }

    @Test
    fun `a multi-select question takes several options`() {
        val question = Question(null, "Which sizes?", listOf(QuestionOption("Small", null), QuestionOption("Large", null)), isMultiSelect = true)

        assertEquals(answers("Which sizes?", "Small, Large"), SpokenQuestions.answer(listOf(question), "small and large"))
    }

    @Test
    fun `the description of a question names each option with its number`() {
        val question = Question("DB", "Which database?", listOf(QuestionOption("Postgres", "Server"), QuestionOption("SQLite", null)), isMultiSelect = false)

        assertEquals(
            listOf("The agent asks: Which database?", "Option 1, Postgres.", "Option 2, SQLite.", "Say an option, or answer in your own words."),
            SpokenQuestions.describe(listOf(question)),
        )
    }

    private fun answers(question: String, answer: String) = "My answers to your questions:\n1. $question\n   $answer"
}
