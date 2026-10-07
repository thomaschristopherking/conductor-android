package build.conductor.android.client.data.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuestionsTest {
    private val colour = Question("Colour", "Which colour?", listOf(QuestionOption("Red", null), QuestionOption("Green", null)), isMultiSelect = false)
    private val sizes = Question("Size", "Which sizes?", listOf(QuestionOption("Small", null), QuestionOption("Large", null)), isMultiSelect = true)
    private val card = TranscriptItem.Questions("m1#0", "t1", listOf(colour, sizes))

    @Test
    fun `the result of a question moves into its card and leaves no result row`() {
        val result = TranscriptItem.ToolResult("m2#0", "User responses:\n1. Green", isError = false, toolUseId = "t1")
        val other = TranscriptItem.ToolResult("m3#0", "ok", isError = false, toolUseId = "t9")

        val items = attachQuestionResults(listOf(card, result, other))

        assertEquals(listOf(card.copy(result = "User responses:\n1. Green"), other), items)
    }

    @Test
    fun `the open question is the last card with no result and no turn end after it`() {
        assertEquals(card, findOpenQuestion(listOf(TranscriptItem.AssistantText("a", "Hi"), card)))
        assertNull(findOpenQuestion(listOf(card.copy(result = "User responses:\n1. Red"))))
        assertNull(findOpenQuestion(listOf(card, TranscriptItem.TurnEnd("e", isError = false, durationMillis = null, costUsd = null))))
    }

    @Test
    fun `answers become one numbered message that names each question`() {
        val answers = listOf(QuestionAnswer(selectedLabels = setOf("Green")), QuestionAnswer(selectedLabels = setOf("Small", "Large"), otherText = "Huge"))

        assertEquals(
            "My answers to your questions:\n1. Which colour?\n   Green\n2. Which sizes?\n   Small, Large, Huge",
            formatAnswers(listOf(colour, sizes), answers),
        )
    }

    @Test
    fun `a question without an answer says so`() {
        assertEquals(
            "My answers to your questions:\n1. Which colour?\n   No answer\n2. Which sizes?\n   Large",
            formatAnswers(listOf(colour, sizes), listOf(QuestionAnswer(), QuestionAnswer(selectedLabels = setOf("Large")))),
        )
    }

    @Test
    fun `a single choice replaces the last choice and clears other text, and other text clears the choice`() {
        val chosen = QuestionAnswer(otherText = "Blue").withToggled("Red", colour)
        assertEquals(QuestionAnswer(selectedLabels = setOf("Red")), chosen)
        assertEquals(QuestionAnswer(selectedLabels = setOf("Green")), chosen.withToggled("Green", colour))
        assertEquals(QuestionAnswer(otherText = "Blue"), chosen.withOtherText("Blue", colour))
    }

    @Test
    fun `multiple choices add and remove labels and keep other text`() {
        val answer = QuestionAnswer(otherText = "Huge").withToggled("Small", sizes).withToggled("Large", sizes).withToggled("Small", sizes)

        assertEquals(QuestionAnswer(selectedLabels = setOf("Large"), otherText = "Huge"), answer)
    }

    @Test
    fun `a result line says how the question ended`() {
        assertEquals("Answered in Conductor:\n1. Push back\n2. Keep printing it", describeQuestionResult("User responses:\n1. Push back\n2. Keep printing it"))
        assertEquals(
            "Closed by a message or by Stop.",
            describeQuestionResult("User cancelled the question.\nError code: CONDUCTOR_ASK_USER_QUESTION_USER_CANCELLED\nRetryable: no"),
        )
        assertEquals("Error asking the user a question: boom", describeQuestionResult("Error asking the user a question: boom\nError code: X"))
    }

    @Test
    fun `the open question id carries across chunks of messages`() {
        val opened = findOpenQuestionId(null, listOf(card))
        assertEquals("t1", opened)
        assertEquals("t1", findOpenQuestionId(opened, listOf(TranscriptItem.AssistantText("a", "Hi"))))
        assertEquals("t1", findOpenQuestionId(opened, listOf(TranscriptItem.ToolResult("r0", "ok", isError = false, toolUseId = "t9"))))
        assertNull(findOpenQuestionId(opened, listOf(TranscriptItem.ToolResult("r1", "User responses: 1. Red", isError = false, toolUseId = "t1"))))
        assertNull(findOpenQuestionId(opened, listOf(TranscriptItem.TurnEnd("e", isError = false, durationMillis = null, costUsd = null))))
    }
}
