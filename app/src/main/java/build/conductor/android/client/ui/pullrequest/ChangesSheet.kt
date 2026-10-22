package build.conductor.android.client.ui.pullrequest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import build.conductor.android.client.R
import build.conductor.android.client.data.github.ChangeKind
import build.conductor.android.client.data.github.FileChange
import build.conductor.android.client.data.github.PullRequestStack
import build.conductor.android.client.data.github.PullRequestSummary
import build.conductor.android.client.ui.components.LoadState

/** The files that the selected tier changes, grouped by folder, as one list, or by the kind of change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangesSheet(state: PullRequestUiState, viewModel: PullRequestViewModel) {
    val stack = (state.lookup as? StackLookup.Found)?.stack ?: return
    val tier = state.selectedTier ?: return
    ModalBottomSheet(onDismissRequest = viewModel::hideChanges, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ChangesContent(stack, tier, state, viewModel)
    }
}

@Composable
internal fun ChangesContent(stack: PullRequestStack, tier: PullRequestSummary, state: PullRequestUiState, viewModel: PullRequestViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        SheetHeader(tier)
        if (stack.tiers.size > 1) TierChips(stack, tier.number, viewModel::selectTier)
        GroupingButtons(state.grouping, viewModel::selectGrouping)
        when (val changes = state.changes[tier.number] ?: LoadState.Loading) {
            LoadState.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is LoadState.Failed -> ChangesError(changes.message, viewModel::retryChanges)
            is LoadState.Loaded -> ChangeList(changes.value, state, tier, viewModel)
        }
    }
}

@Composable
private fun SheetHeader(tier: PullRequestSummary) {
    val uriHandler = LocalUriHandler.current
    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("#${tier.number} ${tier.title}", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${tier.headRef} → ${tier.baseRef}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = { uriHandler.openUri("${tier.url}/files") }) { Text("GitHub") }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(filesLabel(tier.changedFiles) + " changed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        DiffText(tier.diff, MaterialTheme.typography.bodyMedium)
        DiffBlocks(tier.diff)
    }
}

/** The tiers of the stack, from the bottom up, so the user can step through the stack in the sheet. */
@Composable
private fun TierChips(stack: PullRequestStack, selectedNumber: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(stack.tiers.reversed(), key = { it.number }) { tier ->
            FilterChip(
                selected = tier.number == selectedNumber,
                onClick = { onSelect(tier.number) },
                label = { Text("#${tier.number}") },
                leadingIcon = { StateDot(tier.state) },
            )
        }
    }
}

@Composable
private fun GroupingButtons(selected: ChangeGrouping, onSelect: (ChangeGrouping) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ChangeGrouping.entries.forEachIndexed { index, grouping ->
            SegmentedButton(
                selected = grouping == selected,
                onClick = { onSelect(grouping) },
                shape = SegmentedButtonDefaults.itemShape(index, ChangeGrouping.entries.size),
            ) { Text(grouping.label) }
        }
    }
}

@Composable
private fun ChangesError(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun ChangeList(files: List<FileChange>, state: PullRequestUiState, tier: PullRequestSummary, viewModel: PullRequestViewModel) {
    val collapsed = state.collapsedFolders[tier.number].orEmpty()
    val rows = remember(files, state.grouping, collapsed) { changeRows(files, state.grouping, collapsed) }
    if (rows.isEmpty()) {
        Text("This pull request changes no files.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is FolderRow -> FolderLine(row, onClick = { viewModel.toggleFolder(row.path) })
                is KindHeaderRow -> KindHeader(row)
                is FileRow -> FileLine(row, isExpanded = row.change.path in state.expandedFiles, tierUrl = tier.url, onClick = { viewModel.toggleFile(row.change.path) })
            }
        }
    }
}

@Composable
private fun FolderLine(row: FolderRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = indent(row.depth), end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            if (row.isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (row.isExpanded) "Collapse" else "Expand",
            modifier = Modifier.size(18.dp),
        )
        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(row.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(filesLabel(row.fileCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DiffText(row.diff, MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun KindHeader(row: KindHeaderRow) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(kindLabel(row.kind), style = MaterialTheme.typography.titleSmall, color = diffColors().of(row.kind))
            Text(filesLabel(row.fileCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            DiffText(row.diff, MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FileLine(row: FileRow, isExpanded: Boolean, tierUrl: String, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = indent(row.depth), end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KindBadge(row.change.kind)
            Column(modifier = Modifier.weight(1f)) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                row.change.previousPath?.let { Text("from $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            DiffText(row.change.diff, MaterialTheme.typography.labelSmall)
        }
        if (isExpanded) PatchView(row.change.patch, tierUrl)
    }
}

/** "A", "M", "R" or "D" in the colour of the kind of change. */
@Composable
private fun KindBadge(kind: ChangeKind) {
    val color = diffColors().of(kind)
    Box(modifier = Modifier.size(20.dp).background(color.copy(alpha = BADGE_ALPHA), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        Text(kindLetter(kind), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

/** The unified diff of one file. Long lines scroll sideways, as in a terminal. */
@Composable
private fun PatchView(patch: String?, tierUrl: String) {
    val colors = diffColors()
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (patch == null) {
            Text("GitHub sends no diff for this file. It is binary or too large.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            return@Column
        }
        val lines = remember(patch) { patch.lines() }
        BoxWithConstraints {
            // Every line takes the width of the longest line, so the line colours reach the right edge.
            Column(modifier = Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth).width(IntrinsicSize.Max).padding(vertical = 4.dp)) {
                lines.take(MAX_PATCH_LINES).forEach { line -> PatchLine(line, patchLineColor(line, colors)) }
            }
        }
        if (lines.size > MAX_PATCH_LINES) {
            TextButton(onClick = { uriHandler.openUri("$tierUrl/files") }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("${lines.size - MAX_PATCH_LINES} more lines on GitHub")
            }
        }
    }
}

@Composable
private fun PatchLine(line: String, color: Color?) {
    Text(
        line.ifEmpty { " " },
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        softWrap = false,
        color = color ?: MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().background(color?.copy(alpha = LINE_ALPHA) ?: Color.Transparent).padding(horizontal = 12.dp),
    )
}

private fun patchLineColor(line: String, colors: DiffColors): Color? = when {
    line.startsWith("@@") -> colors.merged
    line.startsWith("+") -> colors.added
    line.startsWith("-") -> colors.removed
    else -> null
}

private fun indent(depth: Int) = (16 + depth * 16).dp

private fun filesLabel(count: Int) = if (count == 1) "1 file" else "%,d files".format(count)

private fun kindLabel(kind: ChangeKind) = when (kind) {
    ChangeKind.ADDED -> "Added"
    ChangeKind.MODIFIED -> "Modified"
    ChangeKind.RENAMED -> "Renamed"
    ChangeKind.REMOVED -> "Removed"
}

private fun kindLetter(kind: ChangeKind) = when (kind) {
    ChangeKind.ADDED -> "A"
    ChangeKind.MODIFIED -> "M"
    ChangeKind.RENAMED -> "R"
    ChangeKind.REMOVED -> "D"
}

private const val MAX_PATCH_LINES = 400
private const val BADGE_ALPHA = 0.16f
private const val LINE_ALPHA = 0.12f
