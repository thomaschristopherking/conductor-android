package build.conductor.android.client.data

import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import build.conductor.android.client.data.api.Section
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileHomeSnapshotsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "home_snapshot.json") }
    private val snapshot = HomeSnapshot(listOf(workspace("a", "A")), listOf(Section("s1", "Interop", listOf("a"))))

    @Test
    fun `a written snapshot reads back`() = runTest {
        val snapshots = FileHomeSnapshots(file)

        snapshots.write(snapshot)

        assertEquals(snapshot, FileHomeSnapshots(file).read())
    }

    @Test
    fun `a missing or damaged file reads as no snapshot`() = runTest {
        assertNull(FileHomeSnapshots(file).read())

        file.writeText("{\"workspaces\":[")

        assertNull(FileHomeSnapshots(file).read())
    }

    @Test
    fun `clear removes the snapshot`() = runTest {
        val snapshots = FileHomeSnapshots(file)
        snapshots.write(snapshot)

        snapshots.clear()

        assertNull(snapshots.read())
    }
}
