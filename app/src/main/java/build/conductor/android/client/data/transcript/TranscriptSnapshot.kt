package build.conductor.android.client.data.transcript

/** A parsed transcript and the id of its last message, so the next read asks only for newer messages. */
data class TranscriptSnapshot(
    val items: List<TranscriptItem>,
    val lastMessageId: String?,
    val messageIds: Set<String>,
    val setup: AgentSetup = AgentSetup(),
)
