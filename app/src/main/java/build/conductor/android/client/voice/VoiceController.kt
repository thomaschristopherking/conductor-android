package build.conductor.android.client.voice

import android.content.Context
import build.conductor.android.client.data.ConductorRepository
import build.conductor.android.client.data.RecentCache
import build.conductor.android.client.data.transcript.TranscriptSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Runs at most one voice conversation for the whole app, so it continues when the user leaves the screen or locks the phone. */
class VoiceController(
    private val context: Context,
    private val repository: ConductorRepository,
    private val transcripts: RecentCache<String, TranscriptSnapshot>,
    private val voiceFilter: StateFlow<VoiceFilter>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = MutableStateFlow<ActiveConversation?>(null)

    /** Null when voice mode is off; an ended conversation keeps its last state, so the screen can show why it ended. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<VoiceState?> = active
        .flatMapLatest { it?.conversation?.uiState ?: flowOf(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Returns the reason that voice mode cannot start, or null when it started. */
    fun start(target: VoiceTarget): String? {
        if (!AndroidListener.isAvailable(context)) return NO_RECOGNIZER
        end()
        val devices = AndroidVoiceDevices(context, voiceFilter.value)
        val conversation = VoiceConversation(target, devices.io, AgentLink(repository, target.sessionId, transcripts[target.sessionId]))
        val job = scope.launch {
            try {
                conversation.run()
            } finally {
                devices.release()
            }
        }
        active.value = ActiveConversation(conversation, job)
        VoiceService.start(context)
        return null
    }

    fun tap() {
        active.value?.conversation?.tap()
    }

    fun stopAgent() {
        active.value?.conversation?.stopAgent()
    }

    fun end() {
        active.value?.job?.cancel()
        active.value = null
    }

    private class ActiveConversation(val conversation: VoiceConversation, val job: Job)

    private companion object {
        const val NO_RECOGNIZER = "This phone has no speech recognizer. Install or turn on a voice input app, for example Google, in the Android settings."
    }
}

/** The phone's speaker, recognizer and microphone for one conversation. */
private class AndroidVoiceDevices(context: Context, filter: VoiceFilter) {
    private val speaker = AndroidSpeaker(context)
    private val listener = AndroidListener(context, filter)
    val io = VoiceIo(speaker, listener, MicrophoneOnsetDetector(context, filter))

    fun release() {
        speaker.release()
        listener.release()
    }
}
