package build.conductor.android.client.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import build.conductor.android.client.ConductorApplication
import build.conductor.android.client.MainActivity
import build.conductor.android.client.R
import build.conductor.android.client.notify.StatusNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the microphone and the speaker working while the screen is off, and takes the headset button as a tap. */
class VoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller by lazy { (application as ConductorApplication).container.voiceController }
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = headsetButtonSession()
        scope.launch { followConversation() }
    }

    /** Android requires a call to startForeground after each start request, also when the service already runs. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TAP -> controller.tap()
            ACTION_END -> controller.end()
        }
        showForegroundNotification(controller.state.value)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession?.release()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun followConversation() {
        controller.state.map { state -> state?.takeIf { it.phase != VoicePhase.ENDED }?.let { it.target to it.phase } }.distinctUntilChanged().collect { shown ->
            if (shown == null) stopSelf() else showForegroundNotification(controller.state.value)
        }
    }

    private fun showForegroundNotification(state: VoiceState?) {
        val notification = voiceNotification(this, state ?: return)
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, FOREGROUND_TYPES)
        } catch (_: IllegalStateException) {
            // Android refuses a microphone service that starts from the background; voice mode then works only on screen.
            stopSelf()
        }
    }

    private fun headsetButtonSession() = MediaSession(this, MEDIA_SESSION_TAG).apply {
        setCallback(HeadsetButtonCallback { this@VoiceService.controller.tap() })
        setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_PAUSE).setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        isActive = true
    }

    companion object {
        private const val NOTIFICATION_ID = 0x564F
        private const val MEDIA_SESSION_TAG = "ConductorVoice"
        private const val ACTION_TAP = "build.conductor.android.client.voice.TAP"
        private const val ACTION_END = "build.conductor.android.client.voice.END"
        private val FOREGROUND_TYPES =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
            } catch (_: IllegalStateException) {
                Unit
            }
        }

        private fun actionIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getService(context, action.hashCode(), Intent(context, VoiceService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

        internal fun tapIntent(context: Context) = actionIntent(context, ACTION_TAP)

        internal fun endIntent(context: Context) = actionIntent(context, ACTION_END)
    }
}

/** A single press of a wired or Bluetooth headset button, or play and pause on a watch, counts as one tap. */
private class HeadsetButtonCallback(private val onPress: () -> Unit) : MediaSession.Callback() {
    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
        val event = IntentCompat.getParcelableExtra(mediaButtonIntent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
        if (event.keyCode !in HEADSET_KEYS) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onPress()
        return true
    }

    private companion object {
        val HEADSET_KEYS = setOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE)
    }
}

private const val VOICE_CHANNEL_ID = "voice"

private fun voiceNotification(context: Context, state: VoiceState): Notification {
    createVoiceChannel(context)
    return NotificationCompat.Builder(context, VOICE_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_mic)
        .setContentTitle("Voice: ${state.target.title}")
        .setContentText(phaseLabel(state.phase))
        .setContentIntent(openSessionIntent(context, state.target))
        .addAction(0, "Talk or interrupt", VoiceService.tapIntent(context))
        .addAction(0, "End voice", VoiceService.endIntent(context))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
}

private fun openSessionIntent(context: Context, target: VoiceTarget): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .putExtra(StatusNotifier.EXTRA_WORKSPACE_ID, target.workspaceId)
        .putExtra(StatusNotifier.EXTRA_SESSION_ID, target.sessionId)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(context, target.sessionId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

private fun createVoiceChannel(context: Context) {
    val channel = NotificationChannel(VOICE_CHANNEL_ID, "Voice mode", NotificationManager.IMPORTANCE_LOW)
    channel.description = "Shows while you talk to an agent by voice."
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/** A short word for each phase, for the notification and the voice screen. */
fun phaseLabel(phase: VoicePhase): String = when (phase) {
    VoicePhase.STARTING -> "Starting"
    VoicePhase.LISTENING -> "Listening"
    VoicePhase.HEARING -> "Hearing you"
    VoicePhase.CONFIRMING -> "About to send"
    VoicePhase.SENDING -> "Sending"
    VoicePhase.SPEAKING -> "Speaking"
    VoicePhase.PAUSED -> "Paused"
    VoicePhase.ENDED -> "Voice off"
}
