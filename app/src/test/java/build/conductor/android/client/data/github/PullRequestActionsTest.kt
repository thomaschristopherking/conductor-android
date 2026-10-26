package build.conductor.android.client.data.github

import build.conductor.android.client.data.api.ApiException
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class PullRequestActionsTest {
    private val server = MockWebServer()
    private val logStorage = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val responses = mutableMapOf<String, MockResponse>()
    private lateinit var actions: ApiPullRequestActions
    private val example = RepositoryName("example-org", "example-repo")

    @Before
    fun setUp() {
        server.dispatcher = recordingDispatcher(responses)
        logStorage.dispatcher = recordingDispatcher(mutableMapOf("/log" to MockResponse.Builder().body(LOG).build()))
        server.start()
        logStorage.start()
        actions = ApiPullRequestActions(createGitHubApi(token = { TOKEN }, baseUrl = server.url("/"), retryDelay = {}))
    }

    @After
    fun tearDown() {
        server.close()
        logStorage.close()
    }

    @Test
    fun `checks join the check runs and the commit statuses of the head commit`() = runTest {
        respond("/repos/example-org/example-repo/commits/abc/check-runs", CHECK_RUNS)
        respond("/repos/example-org/example-repo/commits/abc/status", """{"state":"failure","statuses":[{"context":"ci/circleci","state":"failure","description":"Tests failed","target_url":"https://circleci.com/1"}]}""")

        val checks = actions.checks(example, "abc")

        assertEquals(listOf("dco", "trufflehog", "ci/circleci"), checks.map { it.name })
        assertEquals(listOf(CheckOutcome.FAILED, CheckOutcome.PENDING, CheckOutcome.FAILED), checks.map { it.outcome })
        assertEquals(121516740036L, checks.first().actionsJobId)
        assertEquals("Tests failed", checks.last().summary)
        assertTrue(requests.all { it.url.encodedQuery == "per_page=100" })
    }

    @Test
    fun `a token without access to checks gets a message that names the permission`() = runTest {
        respond("/repos/example-org/example-repo/commits/abc/check-runs", """{"message":"Resource not accessible by personal access token"}""", code = 403)
        respond("/repos/example-org/example-repo/commits/abc/status", """{"statuses":[]}""")

        val failure = runCatching { actions.checks(example, "abc") }.exceptionOrNull()

        assertEquals(
            "GitHub did not give access (HTTP 403). The token must have read access to the checks and commit statuses of this repository.",
            failure?.message,
        )
    }

    @Test
    fun `a job log follows the redirect without the token and gives the excerpt`() = runTest {
        responses["/repos/example-org/example-repo/actions/jobs/7/logs"] = MockResponse.Builder().code(302).setHeader("Location", logStorage.url("/log")).build()
        val failed = Check("dco", CheckOutcome.FAILED, null, null, actionsJobId = 7)

        val failures = actions.checkFailures(example, listOf(failed))

        assertEquals("##[error]Commit is not signed off.", failures.single().logExcerpt)
        val download = logStorage.takeRequest()
        assertNull(download.headers["Authorization"])
    }

    @Test
    fun `a log that the app cannot read gives a failure without an excerpt`() = runTest {
        val failed = Check("dco", CheckOutcome.FAILED, null, null, actionsJobId = 8)
        val status = Check("ci/circleci", CheckOutcome.FAILED, null, "Tests failed", actionsJobId = null)

        val failures = actions.checkFailures(example, listOf(failed, status))

        assertEquals(listOf(CheckFailure(failed, null), CheckFailure(status, null)), failures)
    }

    @Test
    fun `merge methods are the ones that the repository allows, squash first`() = runTest {
        respond("/repos/example-org/example-repo", """{"allow_squash_merge":true,"allow_merge_commit":false,"allow_rebase_merge":true}""")

        assertEquals(listOf(MergeMethod.SQUASH, MergeMethod.REBASE), actions.mergeMethods(example))
    }

    @Test
    fun `a merge sends a PUT with the method and the head commit`() = runTest {
        respond("/repos/example-org/example-repo/pulls/13/merge", """{"sha":"def","merged":true,"message":"Pull Request successfully merged"}""")

        actions.merge(example, MergeRequest(13, "abc", MergeMethod.SQUASH))

        val request = requests.single()
        assertEquals("PUT", request.method)
        assertEquals("""{"merge_method":"squash","sha":"abc"}""", request.body?.utf8())
        assertEquals("Bearer $TOKEN", request.headers["Authorization"])
    }

    @Test
    fun `a refused merge gives the GitHub message, and a token without write access names the permission`() = runTest {
        respond("/repos/example-org/example-repo/pulls/13/merge", """{"message":"Head branch was modified. Review and try the merge again."}""", code = 409)
        val modified = runCatching { actions.merge(example, MergeRequest(13, "abc", MergeMethod.SQUASH)) }.exceptionOrNull()
        assertTrue(modified is ApiException.Client)
        assertEquals("Head branch was modified. Review and try the merge again.", modified?.message)

        respond("/repos/example-org/example-repo/pulls/13/merge", """{"message":"Resource not accessible by personal access token"}""", code = 403)
        val forbidden = runCatching { actions.merge(example, MergeRequest(13, "abc", MergeMethod.SQUASH)) }.exceptionOrNull()
        assertEquals("GitHub did not give access (HTTP 403). The token must have write access to the contents of this repository.", forbidden?.message)
    }

    private fun recordingDispatcher(routes: Map<String, MockResponse>) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            return routes[request.url.encodedPath] ?: MockResponse.Builder().code(404).body("""{"message":"Not Found"}""").build()
        }
    }

    private fun respond(path: String, body: String, code: Int = 200) {
        responses[path] = MockResponse.Builder().code(code).setHeader("Content-Type", "application/json").body(body).build()
    }

    private companion object {
        const val TOKEN = "github-test-token"
        const val LOG = "2026-10-07T12:13:25.1275518Z ##[error]Commit is not signed off.\n2026-10-07T12:13:25.3360480Z Cleaning up orphan processes\n"
        val CHECK_RUNS = """
            {"total_count":2,"check_runs":[
             {"id":121516740036,"name":"dco","status":"completed","conclusion":"failure",
              "html_url":"https://github.com/example-org/example-repo/actions/runs/40306481367/job/121516740036",
              "output":{"title":null,"summary":null},"app":{"slug":"github-actions"}},
             {"id":121516740834,"name":"trufflehog","status":"in_progress","conclusion":null,
              "html_url":"https://github.com/example-org/example-repo/actions/runs/40306481416/job/121516740834",
              "output":{"title":null,"summary":null},"app":{"slug":"github-actions"}}]}
        """.trimIndent()
    }
}
