package build.conductor.android.client.ui.home

import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import build.conductor.android.client.data.api.Section
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceGroupsTest {
    @Test
    fun `sections come first in API order and other workspaces come last`() {
        val sections = listOf(Section("s2", "Second", listOf("b")), Section("s1", "First", listOf("a")))

        val groups = groupBySection(listOf(workspace("a", "A"), workspace("b", "B"), workspace("c", "C")), sections)

        assertEquals(listOf("Second", "First", "Other workspaces"), groups.map { it.title })
        assertEquals(listOf(listOf("b"), listOf("a"), listOf("c")), groups.map { group -> group.workspaces.map { it.id } })
    }

    @Test
    fun `a section without a listed workspace is left out`() {
        val sections = listOf(Section("s1", "Archived only", listOf("archived")), Section("s2", "Live", listOf("a")))

        val groups = groupBySection(listOf(workspace("a", "A")), sections)

        assertEquals(listOf("Live"), groups.map { it.title })
    }

    @Test
    fun `without sections one untitled group holds every workspace`() {
        val groups = groupBySection(listOf(workspace("a", "A"), workspace("b", "B")), emptyList())

        assertEquals(listOf<String?>(null), groups.map { it.title })
        assertEquals(listOf("a", "b"), groups.single().workspaces.map { it.id })
    }

    @Test
    fun `the emoji goes before the section name`() {
        val groups = groupBySection(listOf(workspace("a", "A")), listOf(Section("s1", "Launch", listOf("a"), emoji = "🚀")))

        assertEquals("🚀 Launch", groups.single().title)
    }

    @Test
    fun `each group lists the newest activity first`() {
        val workspaces = listOf(
            workspace("old", "Old", "2026-10-01T00:00:00Z"),
            workspace("new", "New", "2026-10-04T00:00:00Z"),
            workspace("mid", "Mid", "2026-10-02T00:00:00Z"),
        )

        val groups = groupBySection(workspaces, listOf(Section("s1", "All", listOf("old", "new", "mid"))))

        assertEquals(listOf("new", "mid", "old"), groups.single().workspaces.map { it.id })
    }
}
