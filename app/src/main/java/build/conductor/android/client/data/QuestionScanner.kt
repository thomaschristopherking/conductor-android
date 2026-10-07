package build.conductor.android.client.data

import build.conductor.android.client.data.transcript.TranscriptParser
import build.conductor.android.client.data.transcript.findOpenQuestionId
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/** Where a scan of a transcript stopped, and the question that was open at that point. */
@Serializable
data class QuestionScan(val cursor: String? = null, val openQuestionId: String? = null)

/** The status endpoint says "working" while a question waits, so only the transcript shows an open question. */
class QuestionScanner(private val repository: ConductorRepository) {
    /** Reads the messages after [from], because the API cannot return the newest messages first. */
    suspend fun scan(sessionId: String, from: QuestionScan): QuestionScan {
        var scan = from
        do {
            val page = repository.messagesAfter(sessionId, scan.cursor)
            val openQuestionId = findOpenQuestionId(scan.openQuestionId, TranscriptParser.parse(page.data))
            scan = QuestionScan(page.data.lastOrNull()?.id ?: scan.cursor, openQuestionId)
        } while (page.hasMore && page.data.isNotEmpty())
        return scan
    }
}

/** Keeps the last scan of each session in memory, so a second look reads only the new messages. */
class OpenQuestionTracker(private val scanner: QuestionScanner) {
    private val scans = ConcurrentHashMap<String, QuestionScan>()

    suspend fun openQuestionId(sessionId: String): String? {
        val scan = scanner.scan(sessionId, scans[sessionId] ?: QuestionScan())
        scans[sessionId] = scan
        return scan.openQuestionId
    }
}
