package build.conductor.android.client.update

import build.conductor.android.client.data.github.AppRelease
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Downloads the APK into [directory]. The client sends no token, because the link of a release asset is public. */
class HttpApkDownloader(
    private val directory: File,
    private val client: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ApkDownloader {
    override suspend fun download(release: AppRelease, onProgress: (Float?) -> Unit): File = withContext(ioDispatcher) {
        val apk = File(directory.apply { mkdirs() }, APK_FILE_NAME)
        client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub answered HTTP ${response.code}.")
            copyWithProgress(response.body, apk, onProgress)
        }
        if (apk.length() != release.apkSize) throw IOException("The file has ${apk.length()} bytes, not ${release.apkSize}.")
        apk
    }

    private suspend fun copyWithProgress(body: ResponseBody, apk: File, onProgress: (Float?) -> Unit) {
        val length = body.contentLength().takeIf { it > 0 }
        var copied = 0L
        val buffer = ByteArray(BUFFER_BYTES)
        body.byteStream().use { input ->
            apk.outputStream().use { output ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer).takeIf { it >= 0 } ?: break
                    output.write(buffer, 0, count)
                    copied += count
                    onProgress(length?.let { copied.toFloat() / it })
                }
            }
        }
    }

    private companion object {
        const val APK_FILE_NAME = "conductor-android.apk"
        const val BUFFER_BYTES = 64 * 1024
        const val TIMEOUT_SECONDS = 60L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder().readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    }
}
