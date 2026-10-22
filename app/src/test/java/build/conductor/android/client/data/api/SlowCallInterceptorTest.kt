package build.conductor.android.client.data.api

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Invocation
import java.util.concurrent.TimeUnit

class SlowCallInterceptorTest {
    private val server = MockWebServer()
    private val readTimeouts = mutableListOf<Int>()
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(SlowCallInterceptor())
        .addInterceptor(Interceptor { chain -> readTimeouts += chain.readTimeoutMillis(); chain.proceed(chain.request()) })
        .build()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `the archive call waits 120 seconds, and other calls keep the normal timeout`() {
        server.enqueue(MockResponse.Builder().code(200).build())
        server.enqueue(MockResponse.Builder().code(200).build())

        client.newCall(requestFor("archiveWorkspace")).execute().close()
        client.newCall(requestFor("cancelSession")).execute().close()

        assertEquals(listOf(120_000, 30_000), readTimeouts)
    }

    private fun requestFor(methodName: String): Request {
        val method = ConductorApi::class.java.methods.first { it.name == methodName }
        return Request.Builder().url(server.url("/")).post(ByteArray(0).toRequestBody()).tag(Invocation::class.java, Invocation.of(method, listOf("id"))).build()
    }
}
