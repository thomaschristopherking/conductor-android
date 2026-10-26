package build.conductor.android.client.data.github

enum class CheckOutcome {
    PASSED,
    FAILED,
    PENDING,

    /** Skipped, neutral, cancelled or stale: the check neither passed nor failed. */
    NEUTRAL,
}

/** One check run or commit status on the head commit of a pull request. [actionsJobId] is set for a GitHub Actions job, which has a log. */
data class Check(
    val name: String,
    val outcome: CheckOutcome,
    val url: String?,
    val summary: String?,
    val actionsJobId: Long?,
)

/** A failed check and the end of its log, or null when the check has no log or the app could not read it. */
data class CheckFailure(val check: Check, val logExcerpt: String?)

fun GitHubCheckRun.toCheck() = Check(
    name = name,
    outcome = checkRunOutcome(status, conclusion),
    url = htmlUrl,
    summary = output?.title ?: output?.summary,
    actionsJobId = id.takeIf { app?.slug == GITHUB_ACTIONS_SLUG },
)

private fun checkRunOutcome(status: String, conclusion: String?): CheckOutcome = when {
    status != COMPLETED_STATUS -> CheckOutcome.PENDING
    conclusion == "success" -> CheckOutcome.PASSED
    conclusion in FAILED_CONCLUSIONS -> CheckOutcome.FAILED
    else -> CheckOutcome.NEUTRAL
}

fun GitHubCommitStatus.toCheck() = Check(
    name = context,
    outcome = when (state) {
        "success" -> CheckOutcome.PASSED
        "failure", "error" -> CheckOutcome.FAILED
        else -> CheckOutcome.PENDING
    },
    url = targetUrl,
    summary = description,
    actionsJobId = null,
)

/**
 * The lines of a GitHub Actions log that explain the failure: the lines up to the last `##[error]` line, because the clean-up steps follow
 * it. Without an error line, the last lines. Null for an empty log.
 */
fun failureExcerpt(logLines: Sequence<String>): String? {
    val recent = ArrayDeque<String>()
    var atLastError: List<String>? = null
    logLines.mapNotNull(::cleanLogLine).forEach { line ->
        recent.addLast(line)
        if (recent.size > MAX_EXCERPT_LINES) recent.removeFirst()
        if (ERROR_MARKER in line) atLastError = recent.toList()
    }
    return (atLastError ?: recent).takeIf { it.isNotEmpty() }?.joinToString("\n")
}

/** Removes the timestamp, the colour codes and the group markers. Null for a line that only closes a group. */
private fun cleanLogLine(line: String): String? {
    val text = line.removePrefix(BYTE_ORDER_MARK).replace(TIMESTAMP, "").replace(COLOUR_CODE, "")
    if (text.startsWith(END_GROUP_MARKER)) return null
    return text.removePrefix(GROUP_MARKER).take(MAX_LINE_LENGTH)
}

/** The prompt that the Fix errors button sends to the agent of the workspace. */
fun fixErrorsPrompt(pullRequest: PullRequestSummary, failures: List<CheckFailure>): String = buildString {
    append("The checks of pull request #${pullRequest.number}, \"${pullRequest.title}\", failed on commit ${pullRequest.headSha.take(SHORT_SHA_LENGTH)}. ")
    append("Find the cause of each failure, fix it, and push the fix.")
    failures.forEach { append("\n\n").append(failureSection(it)) }
}

private fun failureSection(failure: CheckFailure): String = buildString {
    append("## ${failure.check.name}")
    failure.check.url?.let { append("\n$it") }
    failure.check.summary?.takeIf { it.isNotBlank() }?.let { append("\n${it.take(MAX_SUMMARY_LENGTH)}") }
    failure.logExcerpt?.let { append("\n\n```text\n$it\n```") }
}

private const val GITHUB_ACTIONS_SLUG = "github-actions"
private const val COMPLETED_STATUS = "completed"
private val FAILED_CONCLUSIONS = setOf("failure", "timed_out", "startup_failure", "action_required")
private const val MAX_EXCERPT_LINES = 80
private const val MAX_LINE_LENGTH = 300
private const val MAX_SUMMARY_LENGTH = 1000
private const val SHORT_SHA_LENGTH = 7
private const val ERROR_MARKER = "##[error]"
private const val GROUP_MARKER = "##[group]"
private const val END_GROUP_MARKER = "##[endgroup]"
private const val BYTE_ORDER_MARK = "\uFEFF"
private val TIMESTAMP = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z ?""")
private val COLOUR_CODE = Regex("""\u001B\[[0-9;]*[A-Za-z]""")
