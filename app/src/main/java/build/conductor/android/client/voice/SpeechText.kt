package build.conductor.android.client.voice

/** Turns an agent's markdown into short sentences for text to speech; each code block and table becomes one short notice. */
object SpeechText {
    fun sentences(markdown: String): List<String> =
        replaceBlocks(markdown).lines().map(::plainLine).flatMap(::splitSentences).filter { it.isNotBlank() }

    private fun replaceBlocks(markdown: String): String =
        markdown.replace(FENCED_CODE, "\n$CODE_NOTICE\n").replace(TABLE, "\n$TABLE_NOTICE\n")

    private fun plainLine(line: String): String {
        val withoutMarkers = line.replace(LINE_MARKER, "")
        val withoutLinks = withoutMarkers.replace(IMAGE, "$1").replace(LINK, "$1").replace(BARE_URL, LINK_NOTICE)
        val withoutEmphasis = withoutLinks.replace(INLINE_CODE, "$1").replace(EMPHASIS, "")
        return withoutEmphasis.replace(PATH) { it.value.substringAfterLast('/') }.trim()
    }

    private fun splitSentences(line: String): List<String> = line.split(SENTENCE_END).flatMap(::splitLong).map { it.trim() }

    /** Some engines refuse long input, and a short piece lets an interruption resume close to where it stopped. */
    private fun splitLong(sentence: String): List<String> {
        if (sentence.length <= MAX_PIECE_LENGTH) return listOf(sentence)
        val cut = sentence.lastIndexOfAny(charArrayOf(',', ';', ' '), MAX_PIECE_LENGTH).takeIf { it > 0 } ?: MAX_PIECE_LENGTH
        return listOf(sentence.substring(0, cut + 1)) + splitLong(sentence.substring(cut + 1).trimStart())
    }

    const val CODE_NOTICE = "Code skipped."
    const val TABLE_NOTICE = "Table skipped."
    private const val LINK_NOTICE = "a link"
    private const val MAX_PIECE_LENGTH = 280
    private val FENCED_CODE = Regex("```.*?(```|\\z)", RegexOption.DOT_MATCHES_ALL)
    private val TABLE = Regex("(^\\s*\\|.*\\|\\s*$\\n?)+", RegexOption.MULTILINE)
    private val LINE_MARKER = Regex("^\\s*(#{1,6}\\s+|>\\s*|[-*+]\\s+\\[[ xX]]\\s+|[-*+]\\s+)")
    private val IMAGE = Regex("!\\[([^]]*)]\\([^)]*\\)")
    private val LINK = Regex("\\[([^]]+)]\\([^)]*\\)")
    private val BARE_URL = Regex("https?://\\S*[^\\s.,;:!?)]")
    private val INLINE_CODE = Regex("`([^`]*)`")
    private val EMPHASIS = Regex("\\*\\*|__|~~|(?<![\\w*])\\*(?=\\S)|(?<=\\S)\\*(?![\\w*])")

    /** A file path reads better as its file name; a path needs two slashes or a file extension, so "and/or" stays. */
    private val PATH = Regex("(?<![\\w/])[\\w.-]+(/[\\w.-]+){2,}|(?<![\\w/])[\\w.-]+/[\\w-]+\\.\\w+")
    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")
}
