package build.conductor.android.client.ui.home

import build.conductor.android.client.data.api.Section
import build.conductor.android.client.data.api.Workspace

/** One header and its rows on the home screen. A null [title] means that the list has no headers. */
data class WorkspaceGroup(val key: String, val title: String?, val workspaces: List<Workspace>)

/** Puts workspaces under the user's sections, in the order of the API, and the rest in one last group. */
fun groupBySection(workspaces: List<Workspace>, sections: List<Section>): List<WorkspaceGroup> {
    val newestFirst = workspaces.sortedByDescending { it.lastActivityAt ?: it.createdAt }
    val sectionGroups = sections.mapNotNull { section -> sectionGroup(section, newestFirst) }
    val sectionedIds = sections.flatMap { it.workspaceIds }.toSet()
    return sectionGroups + listOfNotNull(otherGroup(newestFirst.filterNot { it.id in sectionedIds }, hasSectionGroups = sectionGroups.isNotEmpty()))
}

private fun sectionGroup(section: Section, workspaces: List<Workspace>): WorkspaceGroup? {
    val members = workspaces.filter { it.id in section.workspaceIds }
    if (members.isEmpty()) return null
    return WorkspaceGroup(section.id, listOfNotNull(section.emoji, section.name).joinToString(" "), members)
}

private fun otherGroup(workspaces: List<Workspace>, hasSectionGroups: Boolean): WorkspaceGroup? =
    workspaces.takeIf { it.isNotEmpty() }?.let { WorkspaceGroup(OTHER_GROUP_KEY, OTHER_GROUP_TITLE.takeIf { hasSectionGroups }, it) }

private const val OTHER_GROUP_KEY = "other"
private const val OTHER_GROUP_TITLE = "Other workspaces"
