package build.conductor.android.client.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import build.conductor.android.client.MainActivity
import build.conductor.android.client.R
import build.conductor.android.client.data.AgentStatus

object StatusNotifier {
    private const val CHANNEL_ID = "agent_status"
    const val EXTRA_SESSION_ID = "build.conductor.android.client.SESSION_ID"
    const val EXTRA_SESSION_TITLE = "build.conductor.android.client.SESSION_TITLE"

    fun notifyFinished(context: Context, session: FinishedSession) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(session.title)
            .setContentText(if (session.status == AgentStatus.ERROR) "The agent stopped with an error." else "The agent is waiting for you.")
            .setContentIntent(openSessionIntent(context, session))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(session.sessionId.hashCode(), notification)
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Agent status", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Tells you when a starred session's agent finishes."
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openSessionIntent(context: Context, session: FinishedSession): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SESSION_ID, session.sessionId)
            .putExtra(EXTRA_SESSION_TITLE, session.title)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            session.sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
