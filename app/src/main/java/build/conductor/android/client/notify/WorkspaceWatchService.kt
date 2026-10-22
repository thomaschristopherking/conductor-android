package build.conductor.android.client.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import build.conductor.android.client.ConductorApplication
import build.conductor.android.client.MainActivity
import build.conductor.android.client.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Checks the subscribed workspaces every few seconds while the app is closed.
 * Conductor sends no push messages, so only a foreground service can notify soon after an agent stops.
 */
class WorkspaceWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val container by lazy { (application as ConductorApplication).container }
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply { setReferenceCounted(false) }
    }

    /** Null until the service reads the subscriptions. */
    @Volatile
    private var workspaceCount: Int? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch { watchWhileSubscribed() }
    }

    /** Android requires a call to startForeground after each start request, also when the service already runs. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showForegroundNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private suspend fun watchWhileSubscribed() {
        container.watchedWorkspaceCount.collectLatest { count ->
            if (count == 0) {
                stopSelf()
            } else {
                workspaceCount = count
                showForegroundNotification()
                checkRepeatedly()
            }
        }
    }

    private suspend fun checkRepeatedly() {
        while (true) {
            val isAgentWorking = container.workspaceWatcher.check()
            keepCpuAwake(isAgentWorking)
            delay(if (isAgentWorking) WORKING_INTERVAL_MILLIS else IDLE_INTERVAL_MILLIS)
        }
    }

    /** While an agent works, the phone must not sleep between checks, or the notification comes late. */
    private fun keepCpuAwake(isAgentWorking: Boolean) {
        if (isAgentWorking) wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS) else if (wakeLock.isHeld) wakeLock.release()
    }

    private fun showForegroundNotification() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, watchNotification(this, workspaceCount), type)
        } catch (_: IllegalStateException) {
            // Android 12 and later can refuse a restart from the background; the periodic check then covers the gap.
            stopSelf()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 0x5741
        private const val WAKE_LOCK_TAG = "Conductor:WorkspaceWatch"
        private const val WORKING_INTERVAL_MILLIS = 15_000L
        private const val IDLE_INTERVAL_MILLIS = 60_000L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 2 * 60_000L

        /** Android 12 and later refuse the start while the app is in the background; the periodic check then covers the gap. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WorkspaceWatchService::class.java))
            } catch (_: IllegalStateException) {
                Unit
            }
        }
    }
}

private const val WATCH_CHANNEL_ID = "workspace_watch"

private fun watchNotification(context: Context, workspaceCount: Int?): Notification {
    createWatchChannel(context)
    val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    return NotificationCompat.Builder(context, WATCH_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Watching subscribed workspaces")
        .setContentText(workspaceCount?.let { if (it == 1) "1 workspace" else "$it workspaces" })
        .setContentIntent(openApp)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
}

private fun createWatchChannel(context: Context) {
    val channel = NotificationChannel(WATCH_CHANNEL_ID, "Background check", NotificationManager.IMPORTANCE_MIN)
    channel.description = "Shows while the app checks your subscribed workspaces. You can hide it."
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
