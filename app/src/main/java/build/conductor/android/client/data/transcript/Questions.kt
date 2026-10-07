package build.conductor.android.client.data.transcript

data class QuestionOption(val label: String, val description: String?)

data class Question(val header: String?, val text: String, val options: List<QuestionOption>, val isMultiSelect: Boolean)

/** What the user chose for one question. [otherText] is the free answer that Conductor offers next to the options. */
data class QuestionAnswer(val selectedLabels: Set<String> = emptySet(), val otherText: String = "") {
    val isAnswered: Boolean
        get() = selectedLabels.isNotEmpty() || otherText.isNotBlank()

    fun withToggled(label: String, question: Question): QuestionAnswer = when {
        question.isMultiSelect && label in selectedLabels -> copy(selectedLabels = selectedLabels - label)
        question.isMultiSelect -> copy(selectedLabels = selectedLabels + label)
        else -> QuestionAnswer(selectedLabels = setOf(label))
    }

    fun withOtherText(text: String, question: Question): QuestionAnswer =
        if (question.isMultiSelect || text.isBlank()) copy(otherText = text) else QuestionAnswer(otherText = text)

    fun describe(): String = (selectedLabels + listOfNotNull(otherText.trim().takeIf { it.isNotEmpty() })).joinToString(", ")
}

/** Moves the tool result of each question into its card, so the chat shows the outcome on the card and not as a separate row. */
fun attachQuestionResults(items: List<TranscriptItem>): List<TranscriptItem> {
    val questionIds = items.filterIsInstance<TranscriptItem.Questions>().map { it.toolUseId }.toSet()
    val results = items.filterIsInstance<TranscriptItem.ToolResult>().filter { it.toolUseId in questionIds }.associateBy { it.toolUseId }
    return items.mapNotNull { item ->
        when {
            item is TranscriptItem.ToolResult && item.toolUseId in questionIds -> null
            item is TranscriptItem.Questions -> results[item.toolUseId]?.let { item.copy(result = it.output) } ?: item
            else -> item
        }
    }
}

/** The last question with no result, if no turn ended after it. */
fun findOpenQuestion(items: List<TranscriptItem>): TranscriptItem.Questions? {
    val lastQuestionIndex = items.indexOfLast { it is TranscriptItem.Questions }
    val question = items.getOrNull(lastQuestionIndex) as? TranscriptItem.Questions ?: return null
    val hasTurnEnded = items.drop(lastQuestionIndex + 1).any { it is TranscriptItem.TurnEnd }
    return question.takeIf { it.result == null && !hasTurnEnded }
}

/** The public API cannot answer the question form, so the answers go as one message; the message also closes the form. */
fun formatAnswers(questions: List<Question>, answers: List<QuestionAnswer>): String {
    val lines = questions.mapIndexed { index, question ->
        val answer = answers.getOrNull(index)?.takeIf { it.isAnswered }?.describe() ?: NO_ANSWER
        "${index + 1}. ${question.text}\n   $answer"
    }
    return (listOf(ANSWERS_HEADING) + lines).joinToString("\n")
}

/** A plain line about how a question ended, from the text of its tool result. */
fun describeQuestionResult(result: String): String = when {
    result.startsWith(ANSWERED_PREFIX) -> "Answered in Conductor:" + result.removePrefix(ANSWERED_PREFIX).trimEnd()
    CANCELLED_CODE in result -> "Closed by a message or by Stop."
    else -> result.lineSequence().first()
}

private const val ANSWERED_PREFIX = "User responses:"
private const val CANCELLED_CODE = "CONDUCTOR_ASK_USER_QUESTION_USER_CANCELLED"
private const val ANSWERS_HEADING = "My answers to your questions:"
private const val NO_ANSWER = "No answer"
