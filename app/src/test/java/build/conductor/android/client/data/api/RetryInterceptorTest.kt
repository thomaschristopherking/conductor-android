package build.conductor.android.client.data.api

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {
    private val server = MockWebServer()
    private val waits = mutableListOf<Long>()
    private val client = OkHttpClient.Builder().addInterceptor(RetryInterceptor(delay = { waits += it })).build()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `a GET is retried after 503 and returns the later success`() {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())

        val response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals(200, response.code)
        assertEquals(listOf(1_000L), waits)
    }

    @Test
    fun `Retry-After sets the wait`() {
        server.enqueue(MockResponse.Builder().code(429).setHeader("Retry-After", "4").build())
        server.enqueue(MockResponse.Builder().code(200).build())

        client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals(listOf(4_000L), waits)
    }

    @Test
    fun `a POST is never retried`() {
        server.enqueue(MockResponse.Builder().code(503).build())

        val response = client.newCall(Request.Builder().url(server.url("/")).post("{}".toRequestBody()).build()).execute()

        assertEquals(503, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `retries stop after two extra attempts`() {
        repeat(4) { server.enqueue(MockResponse.Builder().code(502).build()) }

        val response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals(502, response.code)
        assertEquals(3, server.requestCount)
        assertEquals(listOf(1_000L, 2_000L), waits)
    }
}
