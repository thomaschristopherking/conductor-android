package build.conductor.android.client.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Installs the APK with a [PackageInstaller] session. [UpdateInstallReceiver] gets the result. */
class PackageInstallerApkInstaller(private val context: Context) : ApkInstaller {
    override val installedBuild: Int by lazy { PackageInfoCompat.getLongVersionCode(packageInfo()).toInt() }

    @Suppress("DEPRECATION") // The flags overload needs API 33.
    private fun packageInfo() = context.packageManager.getPackageInfo(context.packageName, 0)

    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        try {
            commitSession(apk)
        } catch (exception: RuntimeException) {
            throw IOException(exception.message, exception)
        }
    }

    private fun commitSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(sessionParams())
        installer.openSession(sessionId).use { session ->
            session.openWrite(apk.name, 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            session.commit(resultReceiver().intentSender)
        }
    }

    private fun sessionParams() = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
    }

    /** Mutable, because the installer adds the status to the intent. */
    private fun resultReceiver(): PendingIntent {
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, RESULT_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
    }

    private companion object {
        const val RESULT_REQUEST_CODE = 1
    }
}
