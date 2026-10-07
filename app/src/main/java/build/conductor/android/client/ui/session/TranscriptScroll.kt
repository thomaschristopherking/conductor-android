package build.conductor.android.client.ui.session

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * Keeps the transcript at its newest row until the user scrolls back to older rows.
 * The list uses `reverseLayout`, so index 0 is the newest row and the list opens at it.
 */
@Stable
class TranscriptScrollState internal constructor(
    internal val listState: LazyListState,
    isFollowingNewest: MutableState<Boolean>,
) {
    var isFollowingNewest by isFollowingNewest
        private set

    /** Makes the next new row scroll into view, for example after the user sends a prompt. */
    fun followNewest() {
        isFollowingNewest = true
    }

    suspend fun scrollToNewest() = listState.animateScrollToItem(NEWEST_ROW)

    internal suspend fun keepNewestInView() {
        if (isFollowingNewest) listState.scrollToItem(NEWEST_ROW)
    }

    /** A new row moves the list to keep the old rows in place, so only a move with the same row count is the user's. */
    internal suspend fun trackUserPosition() {
        var lastRowCount = UNKNOWN_ROW_COUNT
        snapshotFlow { listState.layoutInfo.totalItemsCount to !listState.canScrollBackward }
            .collect { (rowCount, isAtNewest) ->
                if (rowCount == lastRowCount) isFollowingNewest = isAtNewest
                lastRowCount = rowCount
            }
    }

    private companion object {
        const val NEWEST_ROW = 0
        const val UNKNOWN_ROW_COUNT = -1
    }
}

@Composable
fun rememberTranscriptScrollState(): TranscriptScrollState {
    val listState = rememberLazyListState()
    val isFollowingNewest = rememberSaveable { mutableStateOf(true) }
    return remember(listState, isFollowingNewest) { TranscriptScrollState(listState, isFollowingNewest) }
}
