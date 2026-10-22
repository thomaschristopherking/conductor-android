package build.conductor.android.client.ui.pullrequest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import build.conductor.android.client.data.github.ChangeKind
import build.conductor.android.client.data.github.DiffCount
import build.conductor.android.client.data.github.PullRequestState

/** GitHub's colours for a diff and a pull request state, in a light and a dark version. */
class DiffColors(val added: Color, val removed: Color, val modified: Color, val merged: Color, val neutral: Color) {
    fun of(state: PullRequestState): Color = when (state) {
        PullRequestState.OPEN -> added
        PullRequestState.DRAFT -> neutral
        PullRequestState.MERGED -> merged
        PullRequestState.CLOSED -> removed
    }

    fun of(kind: ChangeKind): Color = when (kind) {
        ChangeKind.ADDED -> added
        ChangeKind.MODIFIED -> modified
        ChangeKind.RENAMED -> merged
        ChangeKind.REMOVED -> removed
    }
}

@Composable
fun diffColors(): DiffColors {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    return if (MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE) {
        DiffColors(Color(0xFF3FB950), Color(0xFFF85149), Color(0xFFD29922), Color(0xFFA371F7), neutral)
    } else {
        DiffColors(Color(0xFF1A7F37), Color(0xFFCF222E), Color(0xFF9A6700), Color(0xFF8250DF), neutral)
    }
}

/** "+1,005 −415" in green and red. */
@Composable
fun DiffText(diff: DiffCount, style: TextStyle = MaterialTheme.typography.labelMedium) {
    val colors = diffColors()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("+%,d".format(diff.additions), color = colors.added, style = style, fontWeight = FontWeight.SemiBold)
        Text("−%,d".format(diff.deletions), color = colors.removed, style = style, fontWeight = FontWeight.SemiBold)
    }
}

/** Five small blocks, green for the share of added lines and red for the share of removed lines. */
@Composable
fun DiffBlocks(diff: DiffCount) {
    val colors = diffColors()
    val (green, red) = diffBlocks(diff)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(DIFF_BLOCKS) { index ->
            val color = when {
                index < green -> colors.added
                index < green + red -> colors.removed
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

private const val DARK_SURFACE_LUMINANCE = 0.5f
