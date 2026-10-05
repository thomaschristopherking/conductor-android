package build.conductor.android.client.data.api

import app.cash.turbine.test
import build.conductor.android.client.Fixtures
import build.conductor.android.client.data.ApiConductorRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConductorApiTest {
    private val server = MockWebServer()
    private lateinit var repository: ApiConductorRepository

    @Before
    fun setUp() {
        server.start()
        repository = ApiConductorRepository(createConductorApi(apiKey = { TEST_KEY }, baseUrl = server.url("/"), retryDelay = {}))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `projects fixture parses and the request carries the bearer key`() = runTest {
        enqueueFixture("projects.json")
        enqueueJson(EMPTY_LAST_PAGE)

        val projects = repository.projects()

        assertEquals(5, projects.size)
        assertEquals("example-project-1", projects.first().name)
        val request = server.takeRequest()
        assertEquals("/v0/projects", request.url.encodedPath)
        assertEquals("Bearer $TEST_KEY", request.headers["Authorization"])
    }

    @Test
    fun `projects follows hasMore across pages`() = runTest {
        enqueueJson("""{"data":[{"id":"p1","name":"a","gitRemote":"r"}],"offset":0,"hasMore":true}""")
        enqueueJson("""{"data":[{"id":"p2","name":"b","gitRemote":"r"}],"offset":1,"hasMore":false}""")

        val projects = repository.projects()

        assertEquals(listOf("p1", "p2"), projects.map { it.id })
        server.takeRequest()
        assertEquals("1", server.takeRequest().url.queryParameter("offset"))
    }

    @Test
    fun `workspaces of a project filter by repo and send includeArchived only when set`() = runTest {
        enqueueFixture("workspaces_by_repo.json")
        enqueueFixture("workspaces.json")

        val active = repository.workspaces("project-1", includeArchived = false, offset = 0)
        repository.workspaces("project-1", includeArchived = true, offset = 100)

        assertTrue(active.data.isNotEmpty())
        val first = server.takeRequest().url
        assertEquals("project-1", first.queryParameter("repo"))
        assertNull(first.queryParameter("includeArchived"))
        val second = server.takeRequest().url
        assertEquals("true", second.queryParameter("includeArchived"))
        assertEquals("100", second.queryParameter("offset"))
    }

    @Test
    fun `workspace, project workspaces and workspace status fixtures parse`() = runTest {
        enqueueFixture("workspace.json")
        enqueueFixture("workspace_status.json")
        enqueueFixture("project_workspaces.json")
        val api = createConductorApi(apiKey = { TEST_KEY }, baseUrl = server.url("/"))

        val workspace = repository.workspace("w1")
        val status = api.workspaceStatus("w1")
        val projectWorkspaces = api.workspaces()

        assertTrue(workspace.deepLink.startsWith("conductor://workspace?id="))
        assertEquals(workspace.id, status.workspaceId)
        assertTrue(projectWorkspaces.data.all { it.projectId != null })
    }

    @Test
    fun `sessions, session and session status fixtures parse`() = runTest {
        enqueueFixture("sessions.json")
        enqueueJson(EMPTY_LAST_PAGE)
        enqueueFixture("session.json")
        enqueueFixture("session_status.json")

        val sessions = repository.sessions("w1")
        val session = repository.session(sessions.first().id)
        val status = repository.sessionStatus(session.id)

        assertTrue(sessions.isNotEmpty())
        assertEquals(sessions.first().id, session.id)
        assertTrue(status.status in setOf("idle", "working", "error"))
    }

    @Test
    fun `messages fixtures parse and polling passes the after cursor`() = runTest {
        enqueueFixture("messages_page.json")
        enqueueFixture("messages_after.json")
        enqueueFixture("messages_all_kinds.json")

        val firstPage = repository.messagesAfter("s1", afterMessageId = null)
        val next = repository.messagesAfter("s1", afterMessageId = firstPage.data.last().id)
        val allKinds = repository.messagesAfter("s1", afterMessageId = null)

        assertTrue(firstPage.hasMore)
        assertTrue(next.data.first().sessionIndex > firstPage.data.last().sessionIndex)
        assertTrue(allKinds.data.map { it.type }.containsAll(listOf("userMessage", "agent")))
        assertNull(server.takeRequest().url.queryParameter("after"))
        assertEquals(firstPage.data.last().id, server.takeRequest().url.queryParameter("after"))
    }

    @Test
    fun `me and favourite models fixtures parse`() = runTest {
        enqueueFixture("me.json")
        enqueueFixture("favorite_models.json")

        val me = repository.testApiKey("candidate-key")
        val favorites = repository.favoriteModels()

        assertEquals("Test User", me.name)
        assertTrue(favorites.isNotEmpty())
        assertEquals("Bearer candidate-key", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `send message posts the text and the message id`() = runTest {
        enqueueJson("""{"messageId":"m1","state":"sent","deepLink":"conductor://x"}""", code = 201)

        val sent = repository.sendMessage("s1", "Fix the test", "4b0e7a52-6f5d-4c11-9a39-1d2f3e4a5b6c")

        assertEquals("sent", sent.state)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v0/sessions/s1/messages", request.url.encodedPath)
        val body = request.jsonBody()
        assertEquals("Fix the test", body["message"]?.jsonPrimitive?.content)
        assertEquals("4b0e7a52-6f5d-4c11-9a39-1d2f3e4a5b6c", body["messageId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `create workspace leaves out empty optional fields`() = runTest {
        enqueueJson("""{"workspaceId":"w9","sessionId":"s9","deepLink":"conductor://workspace?id=w9"}""", code = 201)

        val created = repository.createWorkspace(CreateWorkspaceRequest(projectId = "p1", agent = "claude", model = "opus-5-1m"))

        assertEquals("s9", created.sessionId)
        val body = server.takeRequest().jsonBody()
        assertEquals(setOf("projectId", "agent", "model"), body.keys)
    }

    @Test
    fun `create session, rename, archive and cancel use the documented paths`() = runTest {
        enqueueJson("""{"id":"s2","deepLink":"conductor://x","name":"New"}""", code = 201)
        enqueueFixture("workspace.json")
        enqueueJson("""{"workspaceId":"w1","status":"archived"}""")
        enqueueJson("""{"workspaceId":"w1","sessionId":"s2","status":"working","canceledQueuedMessages":1}""")

        repository.createSession(CreateSessionRequest(workspaceId = "w1", agent = "codex", model = "gpt-6.1-sol"))
        repository.renameWorkspace("w1", "Better name")
        repository.archiveWorkspace("w1")
        val cancelled = repository.cancelSession("s2")

        assertEquals(1, cancelled.canceledQueuedMessages)
        assertEquals("/v0/sessions", server.takeRequest().url.encodedPath)
        val rename = server.takeRequest()
        assertEquals("/v0/workspaces/w1/rename", rename.url.encodedPath)
        assertEquals("Better name", rename.jsonBody()["name"]?.jsonPrimitive?.content)
        assertEquals("/v0/workspaces/w1/archive", server.takeRequest().url.encodedPath)
        assertEquals("/v0/sessions/s2/cancel", server.takeRequest().url.encodedPath)
    }

    private fun enqueueFixture(name: String, code: Int = 200) = enqueueJson(Fixtures.read(name), code)

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(MockResponse.Builder().code(code).setHeader("Content-Type", "application/json").body(body).build())
    }

    private fun RecordedRequest.jsonBody() = Json.parseToJsonElement(body!!.utf8()).jsonObject

    private companion object {
        const val TEST_KEY = "test-key-not-real"
        const val EMPTY_LAST_PAGE = """{"data":[],"offset":5,"hasMore":false}"""
    }
}

class ApiErrorMappingTest {
    private val server = MockWebServer()
    private lateinit var repository: ApiConductorRepository

    @Before
    fun setUp() {
        server.start()
        repository = ApiConductorRepository(createConductorApi(apiKey = { "key" }, baseUrl = server.url("/"), retryDelay = {}))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `401 becomes Unauthorized and emits an unauthorized event`() = runTest {
        enqueue(401, Fixtures.read("error_401.json"))

        repository.unauthorizedEvents.test {
            val error = runCatching { repository.projects() }.exceptionOrNull()

            assertTrue(error is ApiException.Unauthorized)
            assertEquals("Unauthorized client request", error?.message)
            awaitItem()
        }
    }

    @Test
    fun `a rejected candidate key does not emit an unauthorized event`() = runTest {
        enqueue(401, Fixtures.read("error_401.json"))

        repository.unauthorizedEvents.test {
            val error = runCatching { repository.testApiKey("bad") }.exceptionOrNull()

            assertTrue(error is ApiException.Unauthorized)
            expectNoEvents()
        }
    }

    @Test
    fun `404 and 400 become Client errors with the server message`() = runTest {
        enqueue(404, Fixtures.read("error_404.json"))
        enqueue(400, Fixtures.read("error_400.json"))

        val notFound = runCatching { repository.sessionStatus("missing") }.exceptionOrNull() as ApiException.Client
        val badRequest = runCatching { repository.messagesAfter("s1", "x") }.exceptionOrNull() as ApiException.Client

        assertEquals("Session not found", notFound.message)
        assertEquals("NOT_FOUND", notFound.code)
        assertEquals("INVALID_REQUEST", badRequest.code)
        assertFalse(notFound.isRetryable)
    }

    @Test
    fun `429 after retries becomes RateLimited`() = runTest {
        repeat(3) { enqueue(429, """{"userMessage":"Slow down"}""") }

        val error = runCatching { repository.projects() }.exceptionOrNull()

        assertTrue(error is ApiException.RateLimited)
        assertTrue((error as ApiException).isRetryable)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `500 with a body that is not JSON becomes Server with a default message`() = runTest {
        enqueue(500, "<html>oops</html>")

        val error = runCatching { repository.projects() }.exceptionOrNull() as ApiException.Server

        assertEquals(500, error.statusCode)
        assertTrue(error.message!!.contains("HTTP 500"))
    }

    @Test
    fun `a closed connection becomes Offline`() = runTest {
        server.close()

        val error = runCatching { repository.projects() }.exceptionOrNull()

        assertTrue(error is ApiException.Offline)
    }

    @Test
    fun `a body with the wrong shape becomes UnexpectedResponse`() = runTest {
        enqueue(200, """{"data":"not a list"}""")

        val error = runCatching { repository.projects() }.exceptionOrNull()

        assertTrue(error is ApiException.UnexpectedResponse)
    }

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }
}
