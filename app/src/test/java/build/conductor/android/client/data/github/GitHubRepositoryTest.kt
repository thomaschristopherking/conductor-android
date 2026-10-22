package build.conductor.android.client.data.github

import build.conductor.android.client.Fixtures
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

class GitHubRepositoryTest {
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val responses = mutableMapOf<String, MockResponse>()
    private lateinit var repository: ApiGitHubRepository
    private val example = RepositoryName("example-org", "example-repo")

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                return responses.entries.filter { path.startsWith(it.key) }.maxByOrNull { it.key.length }?.value ?: MockResponse.Builder().code(404).body("""{"message":"Not Found"}""").build()
            }
        }
        server.start()
        repository = ApiGitHubRepository(createGitHubApi(token = { TOKEN }, baseUrl = server.url("/"), retryDelay = {}))
        respond("/repos/example-org/example-repo/pulls?", Fixtures.read("github_pulls.json"))
        respond("/repos/example-org/example-repo/pulls/13", Fixtures.read("github_pull_13.json"))
        respond("/repos/example-org/example-repo/pulls/14", Fixtures.read("github_pull_14.json"))
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `the workspace link finds the pull request, and the GitHub stack gives the tiers from the top down`() = runTest {
        respond("/repos/example-org/example-repo/stacks?pull_request=13", Fixtures.read("github_stack.json"))

        val stack = repository.workspaceStack(example, WorkspaceKey(772, "renamed workspace"))!!

        assertEquals(13, stack.workspaceNumber)
        assertEquals(listOf(14, 13), stack.tiers.map { it.number })
        assertEquals("main", stack.baseRef)
        assertEquals(DiffCount(1005, 415), stack.workspaceTier.diff)
        assertEquals(PullRequestState.DRAFT, stack.workspaceTier.state)
        val request = requests.first()
        assertEquals("Bearer $TOKEN", request.headers["Authorization"])
        assertEquals("application/vnd.github+json", request.headers["Accept"])
        assertEquals("state=all&sort=updated&direction=desc&per_page=100", request.url.encodedQuery)
    }

    @Test
    fun `without a GitHub stack, the open pull requests that build on each other make the stack`() = runTest {
        val stack = repository.workspaceStack(example, WorkspaceKey(772, "push"))!!

        assertEquals(listOf(14, 13), stack.tiers.map { it.number })
        assertEquals("main", stack.baseRef)
    }

    @Test
    fun `a workspace with no matching pull request has no stack`() = runTest {
        assertNull(repository.workspaceStack(example, WorkspaceKey(9, "nothing-here")))
    }

    @Test
    fun `changed files follow the pages until a short page`() = runTest {
        val fullPage = (1..100).joinToString(",", "[", "]") { """{"filename":"f$it.kt","status":"modified","additions":1,"deletions":0}""" }
        respond("/repos/example-org/example-repo/pulls/13/files?page=1", fullPage)
        respond("/repos/example-org/example-repo/pulls/13/files?page=2", Fixtures.read("github_files.json"))

        val files = repository.changedFiles(example, 13)

        assertEquals(104, files.size)
        val renamed = files.first { it.path == "README.md" }
        assertEquals(ChangeKind.RENAMED, renamed.kind)
        assertEquals("READ_ME.md", renamed.previousPath)
        assertNull(files.first { it.path == "docs/old.md" }.patch)
    }

    @Test
    fun `a rejected token is an unauthorized error with a GitHub message`() = runTest {
        respond("/user", """{"message":"Bad credentials"}""", code = 401)

        val failure = runCatching { repository.testToken("bad") }.exceptionOrNull()

        assertTrue(failure is ApiException.Unauthorized)
        assertEquals("GitHub rejected the token. Paste a new token in Settings.", failure?.message)
        assertEquals("Bearer bad", requests.single().headers["Authorization"])
    }

    private fun respond(pathPrefix: String, body: String, code: Int = 200) {
        responses[pathPrefix] = MockResponse.Builder().code(code).setHeader("Content-Type", "application/json").body(body).build()
    }

    private companion object {
        const val TOKEN = "github-test-token"
    }
}
