package build.conductor.android.client.data

/** The agent status of a session. The API sends `idle`, `working` or `error`. */
enum class AgentStatus {
    WORKING,
    IDLE,
    ERROR,
    UNKNOWN,
    ;

    val isSettled: Boolean
        get() = this == IDLE || this == ERROR

    companion object {
        fun from(value: String?): AgentStatus = when (value) {
            "working" -> WORKING
            "idle" -> IDLE
            "error" -> ERROR
            else -> UNKNOWN
        }
    }
}

/** The lifecycle state of a workspace machine. */
enum class WorkspaceState {
    INITIALIZING,
    READY,
    SLEEPING,
    ARCHIVED,
    DELETED,
    UPDATING,
    UNSTARTED,
    UNKNOWN,
    ;

    companion object {
        fun from(value: String?): WorkspaceState = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
