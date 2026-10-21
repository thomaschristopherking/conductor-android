package build.conductor.android.client.data

import build.conductor.android.client.data.api.Section
import build.conductor.android.client.data.api.Workspace
import build.conductor.android.client.data.api.conductorJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/** The workspaces and sections that the home screen showed last. */
@Serializable
data class HomeSnapshot(val workspaces: List<Workspace>, val sections: List<Section>)

/** The last home screen, so the next app start shows it at once while the list refreshes. */
interface HomeSnapshots {
    /** Null when there is no snapshot, or when it cannot be read. */
    suspend fun read(): HomeSnapshot?
    suspend fun write(snapshot: HomeSnapshot)
    suspend fun clear()

    object None : HomeSnapshots {
        override suspend fun read(): HomeSnapshot? = null
        override suspend fun write(snapshot: HomeSnapshot) = Unit
        override suspend fun clear() = Unit
    }
}

class FileHomeSnapshots(private val file: File) : HomeSnapshots {
    private val mutex = Mutex()

    override suspend fun read(): HomeSnapshot? = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { conductorJson.decodeFromString<HomeSnapshot>(file.readText()) }.getOrNull()
        }
    }

    /** Writes to a second file first, so a stopped write never leaves half a snapshot. */
    override suspend fun write(snapshot: HomeSnapshot) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val partial = File(file.path + PARTIAL_SUFFIX)
            partial.writeText(conductorJson.encodeToString(snapshot))
            partial.renameTo(file)
            Unit
        }
    }

    override suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
        Unit
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".partial"
    }
}
