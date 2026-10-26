package build.conductor.android.client.data.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecksTest {
    @Test
    fun `a check run maps its status and conclusion to an outcome`() {
        assertEquals(CheckOutcome.PENDING, checkRun(status = "in_progress").toCheck().outcome)
        assertEquals(CheckOutcome.PASSED, checkRun(conclusion = "success").toCheck().outcome)
        assertEquals(CheckOutcome.FAILED, checkRun(conclusion = "failure").toCheck().outcome)
        assertEquals(CheckOutcome.FAILED, checkRun(conclusion = "timed_out").toCheck().outcome)
        assertEquals(CheckOutcome.NEUTRAL, checkRun(conclusion = "skipped").toCheck().outcome)
        assertEquals(CheckOutcome.NEUTRAL, checkRun(conclusion = "cancelled").toCheck().outcome)
    }

    @Test
    fun `only a GitHub Actions check run has a job log`() {
        assertEquals(121516740834L, checkRun(appSlug = "github-actions").toCheck().actionsJobId)
        assertNull(checkRun(appSlug = "coderabbitai").toCheck().actionsJobId)
    }

    @Test
    fun `a commit status maps its state to an outcome`() {
        assertEquals(CheckOutcome.FAILED, GitHubCommitStatus("ci/circleci", "error").toCheck().outcome)
        assertEquals(CheckOutcome.PENDING, GitHubCommitStatus("ci/circleci", "pending").toCheck().outcome)
        assertEquals(CheckOutcome.PASSED, GitHubCommitStatus("ci/circleci", "success").toCheck().outcome)
    }

    @Test
    fun `the excerpt ends at the last error line, without timestamps, colour codes or group markers`() {
        val log = listOf(
            "\uFEFF2026-10-07T12:13:22.2927468Z ##[group]Run set -euo pipefail",
            "2026-10-07T12:13:22.2930000Z \u001B[36;1mgit log\u001B[0m",
            "2026-10-07T12:13:22.2940000Z ##[endgroup]",
            "2026-10-07T12:13:25.1275518Z ##[error]Commit d7d9a2b is not signed off.",
            "2026-10-07T12:13:25.1363851Z ##[error]Process completed with exit code 1.",
            "2026-10-07T12:13:25.3360480Z Cleaning up orphan processes",
        )

        val excerpt = failureExcerpt(log.asSequence())

        assertEquals(
            "Run set -euo pipefail\ngit log\n##[error]Commit d7d9a2b is not signed off.\n##[error]Process completed with exit code 1.",
            excerpt,
        )
    }

    @Test
    fun `the excerpt keeps the last 80 lines, and a log without an error line gives its end`() {
        val log = (1..200).map { "2026-10-07T12:13:22.0000000Z line $it" }

        val excerpt = failureExcerpt(log.asSequence())!!.lines()

        assertEquals(80, excerpt.size)
        assertEquals("line 121", excerpt.first())
        assertEquals("line 200", excerpt.last())
        assertNull(failureExcerpt(emptySequence()))
    }

    @Test
    fun `the prompt names the pull request and has a section with the link, summary and log of each failure`() {
        val pullRequest = PullRequestSummary(
            21, "Add merge", PullRequestState.OPEN, "feature", "main", "https://github.com/o/r/pull/21", DiffCount(1, 1), 1,
            headSha = "abcdef0123456789", mergeability = Mergeability.UNSTABLE,
        )
        val build = Check("build", CheckOutcome.FAILED, "https://github.com/o/r/actions/runs/1/job/2", null, 2)
        val review = Check("review", CheckOutcome.FAILED, null, "2 comments need changes", null)

        val prompt = fixErrorsPrompt(pullRequest, listOf(CheckFailure(build, "##[error]Test failed"), CheckFailure(review, null)))

        assertEquals(
            """
            The checks of pull request #21, "Add merge", failed on commit abcdef0. Find the cause of each failure, fix it, and push the fix.

            ## build
            https://github.com/o/r/actions/runs/1/job/2

            ```text
            ##[error]Test failed
            ```

            ## review
            2 comments need changes
            """.trimIndent(),
            prompt,
        )
    }

    private fun checkRun(status: String = "completed", conclusion: String? = null, appSlug: String = "github-actions") =
        GitHubCheckRun(121516740834L, "build", status, conclusion, "https://github.com/o/r/actions/runs/1/job/121516740834", app = GitHubApp(appSlug))
}
