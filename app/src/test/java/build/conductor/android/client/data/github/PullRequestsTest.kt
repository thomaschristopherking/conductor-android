package build.conductor.android.client.data.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PullRequestsTest {
    @Test
    fun `repository names come from GitHub URLs only`() {
        assertEquals(RepositoryName("example-org", "example-repo"), RepositoryName.from("https://github.com/example-org/example-repo"))
        assertEquals(RepositoryName("example-org", "example-repo"), RepositoryName.from("https://github.com/example-org/example-repo.git"))
        assertEquals(RepositoryName("example-org", "example.repo"), RepositoryName.from("git@github.com:example-org/example.repo.git"))
        assertNull(RepositoryName.from("https://gitlab.com/example-org/example-repo"))
    }

    @Test
    fun `the workspace link wins over the branch name, and an open pull request wins over a closed one`() {
        val closedLinked = pullRequest(1, "old", body = "https://app.conductor.build/org/w/772", state = "closed")
        val openLinked = pullRequest(2, "new", body = "https://app.conductor.build/org/w/772")
        val named = pullRequest(3, "conductor/push")

        assertEquals(2, findWorkspacePullRequest(listOf(closedLinked, named, openLinked), WorkspaceKey(772, "push"))?.number)
        assertEquals(1, findWorkspacePullRequest(listOf(closedLinked, named), WorkspaceKey(772, "push"))?.number)
    }

    @Test
    fun `a longer workspace number does not match a shorter one`() {
        val other = pullRequest(1, "x", body = "https://app.conductor.build/org/w/7720")

        assertNull(findWorkspacePullRequest(listOf(other), WorkspaceKey(772, "push")))
    }

    @Test
    fun `the branch name matches the workspace name in lower case with dashes`() {
        val named = pullRequest(5, "conductor/fix-the-importer")

        assertEquals(5, findWorkspacePullRequest(listOf(named), WorkspaceKey(null, "Fix the importer"))?.number)
    }

    @Test
    fun `a chained stack runs from the top down, and a closed pull request stands alone`() {
        val bottom = pullRequest(1, "a", base = "main")
        val middle = pullRequest(2, "b", base = "a")
        val top = pullRequest(3, "c", base = "b")
        val other = pullRequest(4, "d", base = "main")

        assertEquals(listOf(3, 2, 1), chainedStackNumbers(middle, listOf(top, other, bottom, middle)))
        assertEquals(listOf(9), chainedStackNumbers(pullRequest(9, "z", state = "closed"), listOf(top, bottom)))
    }

    @Test
    fun `a pull request from a fork never joins a stack or matches by branch name`() {
        val workspacePullRequest = pullRequest(1, "conductor/push", base = "main").withRepositories("example-org/example-repo")
        val fork = pullRequest(2, "main", base = "main").withRepositories("someone/example-repo", "example-org/example-repo")
        val forkNamed = pullRequest(3, "push").withRepositories("someone/example-repo", "example-org/example-repo")

        assertEquals(listOf(1), chainedStackNumbers(workspacePullRequest, listOf(fork, workspacePullRequest)))
        assertNull(findWorkspacePullRequest(listOf(forkNamed), WorkspaceKey(null, "push")))
    }

    @Test
    fun `a stack position counts from the bottom tier, and a lone pull request has none`() {
        assertEquals(StackPosition(3, 12), stackPosition((12 downTo 1).toList(), 3))
        assertEquals(StackPosition(2, 2), stackPosition(listOf(14, 13), 14))
        assertNull(stackPosition(listOf(13), 13))
        assertNull(stackPosition(listOf(14, 13), 99))
    }

    private fun GitHubPullRequest.withRepositories(head: String, base: String = head) =
        copy(head = this.head.copy(repo = GitHubRepositoryRef(head)), base = this.base.copy(repo = GitHubRepositoryRef(base)))

    @Test
    fun `the state comes from merged, closed and draft`() {
        assertEquals(PullRequestState.MERGED, PullRequestState.of(pullRequest(1, "a", state = "closed").copy(mergedAt = "2026-10-01T00:00:00Z")))
        assertEquals(PullRequestState.CLOSED, PullRequestState.of(pullRequest(1, "a", state = "closed")))
        assertEquals(PullRequestState.DRAFT, PullRequestState.of(pullRequest(1, "a").copy(draft = true)))
        assertEquals(PullRequestState.OPEN, PullRequestState.of(pullRequest(1, "a")))
    }

    private fun pullRequest(number: Int, head: String, base: String = "main", body: String? = null, state: String = "open") = GitHubPullRequest(
        number = number,
        title = "PR $number",
        state = state,
        head = GitHubBranch(head),
        base = GitHubBranch(base),
        htmlUrl = "https://github.com/example-org/example-repo/pull/$number",
        body = body,
    )
}
