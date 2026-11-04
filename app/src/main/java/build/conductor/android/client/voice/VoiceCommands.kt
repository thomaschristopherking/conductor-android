package build.conductor.android.client.voice

import build.conductor.android.client.data.transcript.Question
import build.conductor.android.client.data.transcript.QuestionAnswer
import build.conductor.android.client.data.transcript.formatAnswers
import build.conductor.android.client.data.transcript.formatFreeAnswer

/** A phrase that controls voice mode; the app does not send it to the agent. */
enum class VoiceCommand { STOP_AGENT, REPEAT, CONTINUE, STATUS, PAUSE, END_VOICE, CANCEL }

sealed interface Utterance {
    data class Command(val command: VoiceCommand) : Utterance

    data class Words(val text: String) : Utterance
}

object VoiceCommands {
    /** A command must be the whole utterance, so "stop the build when it fails" goes to the agent as a prompt. */
    fun interpret(text: String): Utterance {
        val phrase = normalize(text)
        return PHRASES.entries.firstOrNull { phrase in it.value }?.let { Utterance.Command(it.key) } ?: Utterance.Words(text.trim())
    }

    fun normalize(text: String): String {
        val words = text.lowercase().replace(PUNCTUATION, "").split(WHITESPACE).filter { it.isNotEmpty() }
        return words.dropWhile { it in LEADING_FILLERS }.dropLastWhile { it in TRAILING_FILLERS }.joinToString(" ")
    }

    private val PHRASES: Map<VoiceCommand, Set<String>> = mapOf(
        VoiceCommand.STOP_AGENT to setOf("stop", "stop the agent", "stop agent", "stop working", "stop the turn", "abort"),
        VoiceCommand.REPEAT to setOf("repeat", "repeat that", "say that again", "say again", "again", "what did you say"),
        VoiceCommand.CONTINUE to setOf("continue", "go on", "keep going", "carry on", "resume", "continue reading"),
        VoiceCommand.STATUS to setOf("status", "whats the status", "what is the status", "what are you doing", "are you done", "progress"),
        VoiceCommand.PAUSE to setOf("pause", "stop listening", "be quiet", "quiet", "sleep", "go to sleep"),
        VoiceCommand.END_VOICE to setOf("end voice", "end voice mode", "exit voice", "exit voice mode", "voice off", "stop voice", "goodbye"),
        VoiceCommand.CANCEL to setOf("cancel", "cancel that", "scratch that", "never mind", "nevermind", "dont send", "do not send"),
    )
    private val PUNCTUATION = Regex("[^\\p{L}\\p{N}\\s]")
    private val WHITESPACE = Regex("\\s+")
    private val LEADING_FILLERS = setOf("ok", "okay", "please", "hey", "um", "uh", "so")
    private val TRAILING_FILLERS = setOf("please", "now", "thanks", "thank")
}

/** Reads a question aloud, and turns a spoken reply into the answer message that the session screen also sends. */
object SpokenQuestions {
    fun describe(questions: List<Question>): List<String> =
        questions.flatMap(::describeOne) + REPLY_HINT

    fun answer(questions: List<Question>, spoken: String): String {
        val question = questions.singleOrNull() ?: return formatFreeAnswer(spoken)
        val labels = matchOptions(question, spoken)
        val answer = if (labels != null) QuestionAnswer(selectedLabels = labels) else QuestionAnswer(otherText = spoken)
        return formatAnswers(questions, listOf(answer))
    }

    private fun describeOne(question: Question): List<String> {
        val options = question.options.mapIndexed { index, option -> "Option ${index + 1}, ${option.label}." }
        val multiple = if (question.isMultiSelect) listOf("You can choose more than one.") else emptyList()
        return listOf("The agent asks: ${question.text}") + options + multiple
    }

    /** Each part of "red and green" must name an option, or the reply goes as free text. */
    private fun matchOptions(question: Question, spoken: String): Set<String>? {
        val parts = spoken.split(PART_SEPARATOR).map(VoiceCommands::normalize).filter { it.isNotEmpty() }
        val labels = parts.map { part -> matchOption(question, part) ?: return null }
        if (labels.isEmpty() || (!question.isMultiSelect && labels.size > 1)) return null
        return labels.toSet()
    }

    private fun matchOption(question: Question, part: String): String? {
        val byLabel = question.options.firstOrNull { VoiceCommands.normalize(it.label) == part }
        return byLabel?.label ?: ordinalOf(part)?.let { question.options.getOrNull(it)?.label }
    }

    private fun ordinalOf(part: String): Int? {
        val word = ORDINAL_PHRASE.matchEntire(part)?.groupValues?.get(1) ?: return null
        return ORDINALS.indexOfFirst { word in it }.takeIf { it >= 0 }
    }

    private const val REPLY_HINT = "Say an option, or answer in your own words."
    private val PART_SEPARATOR = Regex(",|\\band\\b", RegexOption.IGNORE_CASE)
    private val ORDINAL_PHRASE = Regex("(?:the )?(?:option |number )?(\\w+)(?: one| option)?")
    private val ORDINALS = listOf(
        setOf("one", "1", "first"),
        setOf("two", "2", "second"),
        setOf("three", "3", "third"),
        setOf("four", "4", "fourth"),
        setOf("five", "5", "fifth"),
    )
}
