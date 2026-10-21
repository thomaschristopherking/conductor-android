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
    const val EXTRA_WORKSPACE_ID = "build.conductor.android.client.WORKSPACE_ID"
    const val EXTRA_SESSION_ID = "build.conductor.android.client.SESSION_ID"

    fun notifyFinished(context: Context, session: FinishedSession, workspaceId: String) {
        val text = if (session.status == AgentStatus.ERROR) "The agent stopped with an error." else "The agent is waiting for you."
        notify(context, SessionNotice(workspaceId, session.sessionId, session.title), text)
    }

    fun notifyQuestion(context: Context, session: AskingSession, workspaceId: String) {
        notify(context, SessionNotice(workspaceId, session.sessionId, session.title), "The agent asked you a question.")
    }

    private fun notify(context: Context, notice: SessionNotice, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notice.title)
            .setContentText(text)
            .setContentIntent(openSessionIntent(context, notice))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notice.sessionId.hashCode(), notification)
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Agent status", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Tells you when a starred session's agent finishes or asks you a question."
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openSessionIntent(context: Context, notice: SessionNotice): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_WORKSPACE_ID, notice.workspaceId)
            .putExtra(EXTRA_SESSION_ID, notice.sessionId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            notice.sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

private data class SessionNotice(val workspaceId: String, val sessionId: String, val title: String)
