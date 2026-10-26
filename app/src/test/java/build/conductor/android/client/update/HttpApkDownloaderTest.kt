package build.conductor.android.client.update

import build.conductor.android.client.data.github.AppRelease
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class HttpApkDownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val apkBytes = ByteArray(200_000) { (it % 251).toByte() }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `the APK goes to the folder, and the progress ends at the whole file`() = runTest {
        server.enqueue(MockResponse.Builder().body(Buffer().write(apkBytes)).build())
        val progress = mutableListOf<Float?>()

        val apk = downloader().download(release(apkBytes.size.toLong())) { progress += it }

        assertTrue(apk.readBytes().contentEquals(apkBytes))
        assertEquals(1f, progress.last())
        assertEquals(null, server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `an HTTP error and a short file fail the download`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().body(Buffer().write(apkBytes)).build())

        val notFound = runCatching { downloader().download(release(apkBytes.size.toLong())) {} }.exceptionOrNull()
        val short = runCatching { downloader().download(release(apkBytes.size + 1L)) {} }.exceptionOrNull()

        assertEquals("GitHub answered HTTP 404.", (notFound as IOException).message)
        assertEquals("The file has 200000 bytes, not 200001.", (short as IOException).message)
    }

    private fun TestScope.downloader() = HttpApkDownloader(folder.root, ioDispatcher = StandardTestDispatcher(testScheduler))

    private fun release(size: Long) = AppRelease(15, "v0.1.0-release.15", server.url("/conductor-android.apk").toString(), size)
}
