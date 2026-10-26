package build.conductor.android.client.ui.pullrequest

import build.conductor.android.client.data.github.Check
import build.conductor.android.client.data.github.CheckOutcome
import build.conductor.android.client.data.github.DiffCount
import build.conductor.android.client.data.github.Mergeability
import build.conductor.android.client.data.github.PullRequestState
import build.conductor.android.client.data.github.PullRequestSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ChecksLabelsTest {
    @Test
    fun `the label counts failures first, then running checks, then passed and skipped checks`() {
        assertEquals("No checks", checksLabel(emptyList()))
        assertEquals("1 of 3 checks failed", checksLabel(checks(CheckOutcome.FAILED, CheckOutcome.PENDING, CheckOutcome.PASSED)))
        assertEquals("1 of 2 checks running", checksLabel(checks(CheckOutcome.PENDING, CheckOutcome.PASSED)))
        assertEquals("1 check passed", checksLabel(checks(CheckOutcome.PASSED)))
        assertEquals("2 checks passed, 1 skipped", checksLabel(checks(CheckOutcome.PASSED, CheckOutcome.PASSED, CheckOutcome.NEUTRAL)))
    }

    @Test
    fun `the overall outcome is the worst outcome`() {
        assertEquals(CheckOutcome.FAILED, overallOutcome(checks(CheckOutcome.PASSED, CheckOutcome.FAILED, CheckOutcome.PENDING)))
        assertEquals(CheckOutcome.PENDING, overallOutcome(checks(CheckOutcome.PASSED, CheckOutcome.PENDING)))
        assertEquals(CheckOutcome.NEUTRAL, overallOutcome(checks(CheckOutcome.NEUTRAL)))
    }

    @Test
    fun `the merge dialog warns about failed checks and about each merge state that GitHub can refuse`() {
        assertEquals(emptyList<String>(), mergeWarnings(tier(Mergeability.READY), checks(CheckOutcome.PASSED)))
        assertEquals(listOf("Some checks failed."), mergeWarnings(tier(Mergeability.UNSTABLE), checks(CheckOutcome.FAILED)))
        assertEquals(listOf("A check that is not required failed."), mergeWarnings(tier(Mergeability.UNSTABLE), emptyList()))
        assertEquals(listOf("The branch is behind main. GitHub can refuse the merge."), mergeWarnings(tier(Mergeability.BEHIND), emptyList()))
        assertEquals(
            listOf("Some checks still run.", "GitHub blocks the merge until a required review or check passes. An admin can still merge."),
            mergeWarnings(tier(Mergeability.BLOCKED), checks(CheckOutcome.PENDING)),
        )
    }

    private fun checks(vararg outcomes: CheckOutcome) = outcomes.mapIndexed { index, outcome -> Check("check $index", outcome, null, null, null) }

    private fun tier(mergeability: Mergeability) =
        PullRequestSummary(21, "Add merge", PullRequestState.OPEN, "feature", "main", "https://github.com/o/r/pull/21", DiffCount(1, 1), 1, "abc", mergeability)
}
