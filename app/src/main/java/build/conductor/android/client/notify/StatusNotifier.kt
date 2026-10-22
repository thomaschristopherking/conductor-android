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

class StatusNotifier(private val context: Context) : AlertNotifier {
    override fun notify(alert: SessionAlert) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(alert.sessionTitle)
            .setContentText(textFor(alert.alert))
            .setSubText(alert.workspaceName)
            .setContentIntent(openSessionIntent(alert))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(alert.sessionId.hashCode(), notification)
    }

    private fun textFor(alert: AgentAlert): String = when (alert) {
        AgentAlert.FINISHED -> "The agent is waiting for you."
        AgentAlert.FAILED -> "The agent stopped with an error."
        AgentAlert.ASKED -> "The agent asked you a question."
    }

    private fun openSessionIntent(alert: SessionAlert): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_WORKSPACE_ID, alert.workspaceId)
            .putExtra(EXTRA_SESSION_ID, alert.sessionId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, alert.sessionId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Agent status", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Tells you when an agent in a subscribed workspace finishes or asks you a question."
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "agent_status"
        const val EXTRA_WORKSPACE_ID = "build.conductor.android.client.WORKSPACE_ID"
        const val EXTRA_SESSION_ID = "build.conductor.android.client.SESSION_ID"
    }
}
