package build.conductor.android.client.ui.pullrequest

import build.conductor.android.client.data.github.ChangeKind
import build.conductor.android.client.data.github.DiffCount
import build.conductor.android.client.data.github.FileChange

enum class ChangeGrouping(val label: String) {
    FOLDERS("Folders"),
    FILES("Files"),
    KINDS("Type"),
}

/** One line in the list of changed files. */
sealed interface ChangeRow {
    val key: String
    val depth: Int
}

data class FolderRow(val path: String, val label: String, override val depth: Int, val diff: DiffCount, val fileCount: Int, val isExpanded: Boolean) : ChangeRow {
    override val key: String
        get() = "folder/$path"
}

data class FileRow(val change: FileChange, val label: String, override val depth: Int) : ChangeRow {
    override val key: String
        get() = "file/${change.path}"
}

data class KindHeaderRow(val kind: ChangeKind, val diff: DiffCount, val fileCount: Int) : ChangeRow {
    override val key: String
        get() = "kind/$kind"
    override val depth: Int
        get() = 0
}

/** A folder and everything below it. [path] is empty for the root of the repository. */
data class FolderNode(val path: String, val name: String, val folders: List<FolderNode>, val files: List<FileChange>) {
    val diff: DiffCount = (files.map { it.diff } + folders.map { it.diff }).fold(DiffCount.ZERO, DiffCount::plus)
    val fileCount: Int = files.size + folders.sumOf { it.fileCount }
}

fun changeRows(files: List<FileChange>, grouping: ChangeGrouping, collapsedFolders: Set<String>): List<ChangeRow> = when (grouping) {
    ChangeGrouping.FOLDERS -> folderRows(buildFolderTree(files), collapsedFolders, depth = 0)
    ChangeGrouping.FILES -> files.sortedBy { it.path }.map { FileRow(it, it.path, depth = 0) }
    ChangeGrouping.KINDS -> kindRows(files)
}

fun buildFolderTree(files: List<FileChange>): FolderNode = folderNode(path = "", name = "", files.map { it.path.split('/') to it })

private fun folderNode(path: String, name: String, entries: List<Pair<List<String>, FileChange>>): FolderNode {
    val (direct, nested) = entries.partition { (segments, _) -> segments.size == 1 }
    val folders = nested.groupBy { (segments, _) -> segments.first() }.toSortedMap().map { (segment, children) ->
        compact(folderNode(joinPath(path, segment), segment, children.map { (segments, change) -> segments.drop(1) to change }))
    }
    return FolderNode(path, name, folders, direct.map { it.second }.sortedBy { it.path })
}

/** A folder that holds only one folder shows as one row, for example "src/main/java". */
private fun compact(node: FolderNode): FolderNode {
    val only = node.folders.singleOrNull()
    return if (node.files.isEmpty() && only != null) only.copy(name = "${node.name}/${only.name}") else node
}

private fun joinPath(parent: String, segment: String) = if (parent.isEmpty()) segment else "$parent/$segment"

private fun folderRows(node: FolderNode, collapsedFolders: Set<String>, depth: Int): List<ChangeRow> {
    val folderRows = node.folders.flatMap { folder ->
        val isExpanded = folder.path !in collapsedFolders
        val row = FolderRow(folder.path, folder.name, depth, folder.diff, folder.fileCount, isExpanded)
        listOf(row) + if (isExpanded) folderRows(folder, collapsedFolders, depth + 1) else emptyList()
    }
    return folderRows + node.files.map { FileRow(it, it.path.substringAfterLast('/'), depth) }
}

private fun kindRows(files: List<FileChange>): List<ChangeRow> = ChangeKind.entries.flatMap { kind ->
    val members = files.filter { it.kind == kind }.sortedBy { it.path }
    if (members.isEmpty()) return@flatMap emptyList()
    val header = KindHeaderRow(kind, members.map { it.diff }.fold(DiffCount.ZERO, DiffCount::plus), members.size)
    listOf(header) + members.map { FileRow(it, it.path, depth = 0) }
}

/** A large change opens with only the top folders expanded, so the list starts short. */
fun initiallyCollapsedFolders(files: List<FileChange>): Set<String> {
    if (files.size <= EXPAND_ALL_LIMIT) return emptySet()
    return buildFolderTree(files).folders.flatMap { top -> top.folders.flatMap(::folderPaths) }.toSet()
}

private fun folderPaths(node: FolderNode): List<String> = listOf(node.path) + node.folders.flatMap(::folderPaths)

/** The green and red blocks of a diff bar with [DIFF_BLOCKS] blocks, as GitHub draws them. */
fun diffBlocks(diff: DiffCount): Pair<Int, Int> {
    val total = diff.additions + diff.deletions
    if (total == 0) return 0 to 0
    val green = Math.round(DIFF_BLOCKS.toDouble() * diff.additions / total).toInt()
    return green to DIFF_BLOCKS - green
}

const val DIFF_BLOCKS = 5
private const val EXPAND_ALL_LIMIT = 40
