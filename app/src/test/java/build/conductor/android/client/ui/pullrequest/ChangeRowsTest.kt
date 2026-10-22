package build.conductor.android.client.ui.pullrequest

import build.conductor.android.client.data.github.ChangeKind
import build.conductor.android.client.data.github.DiffCount
import build.conductor.android.client.data.github.FileChange
import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeRowsTest {
    private val files = listOf(
        change("app/src/main/java/Archive.kt", ChangeKind.MODIFIED, 10, 2),
        change("app/src/main/java/Stack.kt", ChangeKind.ADDED, 40, 0),
        change("app/src/test/StackTest.kt", ChangeKind.ADDED, 5, 0),
        change("docs/old.md", ChangeKind.REMOVED, 0, 7),
        change("README.md", ChangeKind.MODIFIED, 1, 1),
    )

    @Test
    fun `folders hold their files, a folder with one folder joins it, and each folder adds up its files`() {
        val rows = changeRows(files, ChangeGrouping.FOLDERS, collapsedFolders = emptySet())

        assertEquals(
            listOf("app/src 0", "main/java 1", "Archive.kt 2", "Stack.kt 2", "test 1", "StackTest.kt 2", "docs 0", "old.md 1", "README.md 0"),
            rows.map { "${label(it)} ${it.depth}" },
        )
        val app = rows.first() as FolderRow
        assertEquals(DiffCount(55, 2), app.diff)
        assertEquals(3, app.fileCount)
    }

    @Test
    fun `a collapsed folder hides everything below it`() {
        val rows = changeRows(files, ChangeGrouping.FOLDERS, collapsedFolders = setOf("app/src"))

        assertEquals(listOf("app/src", "docs", "old.md", "README.md"), rows.map(::label))
    }

    @Test
    fun `the files grouping is one list in path order`() {
        val rows = changeRows(files, ChangeGrouping.FILES, collapsedFolders = emptySet())

        assertEquals(files.map { it.path }.sorted(), rows.map(::label))
    }

    @Test
    fun `the type grouping puts a header with totals above each kind of change`() {
        val rows = changeRows(files, ChangeGrouping.KINDS, collapsedFolders = emptySet())

        assertEquals(
            listOf("ADDED", "app/src/main/java/Stack.kt", "app/src/test/StackTest.kt", "MODIFIED", "README.md", "app/src/main/java/Archive.kt", "REMOVED", "docs/old.md"),
            rows.map(::label),
        )
        assertEquals(DiffCount(45, 0), (rows.first() as KindHeaderRow).diff)
    }

    @Test
    fun `a small change opens fully expanded, and a large one opens with only the top folders expanded`() {
        assertEquals(emptySet<String>(), initiallyCollapsedFolders(files))

        val large = (1..41).map { change("lib/part$it/File$it.kt", ChangeKind.ADDED, 1, 0) }
        assertEquals((1..41).map { "lib/part$it" }.toSet(), initiallyCollapsedFolders(large))
    }

    @Test
    fun `the diff bar splits five blocks by share`() {
        assertEquals(0 to 0, diffBlocks(DiffCount(0, 0)))
        assertEquals(5 to 0, diffBlocks(DiffCount(10, 0)))
        assertEquals(4 to 1, diffBlocks(DiffCount(1005, 415)))
        assertEquals(0 to 5, diffBlocks(DiffCount(0, 3)))
    }

    private fun label(row: ChangeRow) = when (row) {
        is FolderRow -> row.label
        is FileRow -> row.label
        is KindHeaderRow -> row.kind.name
    }

    private fun change(path: String, kind: ChangeKind, additions: Int, deletions: Int) = FileChange(path, kind, DiffCount(additions, deletions), patch = null, previousPath = null)
}
