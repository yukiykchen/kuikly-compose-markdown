package com.tencent.kuiklybase.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.tencent.kuikly.compose.foundation.MutatePriority
import com.tencent.kuikly.compose.foundation.gestures.ScrollableState
import com.tencent.kuikly.compose.foundation.lazy.LazyListState
import com.tencent.kuikly.compose.material3.Button
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos

/**
 * Keeps a streaming answer visually anchored while its single host item grows.
 *
 * While the user drags or reads away from the bottom, parsed source continues to accumulate
 * but UI commits are coalesced. This avoids changing a large item's measured height during
 * a Kuikly scroll gesture, which otherwise desynchronizes native content size and can show
 * blank space. A visible-content anchor restores the reading position after each commit.
 */
@Composable
fun rememberStreamingMarkdownHost(
    markdownState: StreamingMarkdownState,
    scrollState: ScrollableState,
    isStreaming: Boolean,
    bottomThresholdPx: Int = 96,
    idleCommitDelayMillis: Long = 80L,
    gestureCooldownMillis: Long = 120L,
    autoFollow: Boolean = true,
    showResumeButton: Boolean = false,
    resumeLabel: String = "回到底部",
): StreamingMarkdownHostState {
    val scope = rememberCoroutineScope()
    val uiUpdatesEnabled by markdownState.uiUpdatesEnabled.collectAsState()
    val scrollWatcher = remember(scrollState, bottomThresholdPx) {
        when (scrollState) {
            is LazyListState -> ScrollWatcher.Lazy(scrollState, bottomThresholdPx)
            else -> ScrollWatcher.Generic(scrollState)
        }
    }
    val state = remember(markdownState, scrollState) {
        StreamingMarkdownHostState(
            markdownState = markdownState,
            scrollState = scrollState,
            scrollWatcher = scrollWatcher,
            isStreaming = isStreaming,
            autoFollow = autoFollow,
            followScope = scope,
        )
    }.also { it.update(isStreaming, autoFollow, scrollWatcher) }
    var resumeJob by remember(markdownState, scrollState) { mutableStateOf<Job?>(null) }

    LaunchedEffect(scrollState, isStreaming, autoFollow, bottomThresholdPx, gestureCooldownMillis) {
        if (!isStreaming) return@LaunchedEffect
        snapshotFlow {
            scrollWatcher.snapshot(
                atBottom = state.atBottom,
                followBottom = state.followBottom,
            )
        }.distinctUntilChanged().collect { snapshot ->
            state.observe(snapshot)
            if (snapshot.hold) {
                markdownState.pauseUiUpdates()
                resumeJob?.cancel()
            } else if (state.needsResume || !uiUpdatesEnabled) {
                resumeJob?.cancel()
                resumeJob = scope.launch {
                    delay(idleCommitDelayMillis)
                    if (gestureCooldownMillis > idleCommitDelayMillis) {
                        delay(gestureCooldownMillis - idleCommitDelayMillis)
                    }
                    val idle = scrollWatcher.snapshot(
                        atBottom = state.atBottom,
                        followBottom = state.followBottom,
                    )
                    if ((state.needsResume || !markdownState.uiUpdatesEnabled.value) && !idle.isScrolling) {
                        state.resume(toBottom = idle.atBottom && state.followBottom)
                    } else if (idle.isScrolling) {
                        state.markResumeNeeded()
                    }
                }
            }
        }
    }

    LaunchedEffect(isStreaming) {
        if (!isStreaming) {
            resumeJob?.cancel()
            resumeJob = null
            state.finish()
        }
    }

    state.showResume = showResumeButton && isStreaming && state.needsResume && !uiUpdatesEnabled
    if (state.showResume) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Button(onClick = { scope.launch { state.resumeToBottom() } }) {
                Text(resumeLabel)
            }
        }
    }
    return state
}

class StreamingMarkdownHostState internal constructor(
    private val markdownState: StreamingMarkdownState,
    private val scrollState: ScrollableState,
    private var scrollWatcher: ScrollWatcher,
    private var isStreaming: Boolean,
    private var autoFollow: Boolean,
    private val followScope: kotlinx.coroutines.CoroutineScope,
) {
    internal var atBottom: Boolean = true
        private set
    internal var followBottom: Boolean = autoFollow
        private set
    internal var needsResume: Boolean = false
        private set
    internal var showResume: Boolean = false

    private var anchor: Anchor? = null
    private var compensating = false
    private var lastScrolling = false
    private var followJob: Job? = null

    internal fun update(
        isStreaming: Boolean,
        autoFollow: Boolean,
        scrollWatcher: ScrollWatcher,
    ) {
        this.isStreaming = isStreaming
        this.autoFollow = autoFollow
        this.scrollWatcher = scrollWatcher
    }

    internal fun observe(snapshot: ScrollSnapshot) {
        if (compensating) return
        atBottom = snapshot.atBottom
        val scrollingStarted = snapshot.isScrolling && !lastScrolling
        lastScrolling = snapshot.isScrolling
        if (snapshot.backward && !snapshot.atBottom) {
            followBottom = false
        }
        if (snapshot.isScrolling) {
            if (scrollingStarted) anchor = scrollWatcher.captureAnchor()
            needsResume = true
        } else if (snapshot.atBottom) {
            anchor = null
            followBottom = autoFollow
        }
    }

    internal suspend fun resume(toBottom: Boolean = false) {
        markdownState.resumeUiUpdates()
        withFrameNanos { }
        withFrameNanos { }
        restoreAnchorOrBottom(toBottom)
        needsResume = false
        if (toBottom) {
            followBottom = autoFollow
            anchor = null
        } else if (atBottom) {
            followBottom = autoFollow
        }
    }

    internal suspend fun resumeToBottom() = resume(toBottom = true)

    suspend fun finish() {
        if (!markdownState.uiUpdatesEnabled.value) markdownState.resumeUiUpdates()
        followJob?.cancel()
        followJob = null
        anchor = null
        needsResume = false
        followBottom = autoFollow
    }

    /** Called after a live UI commit to preserve the visible content anchor. */
    suspend fun afterCommit() {
        if (!markdownState.uiUpdatesEnabled.value || !isStreaming || compensating) return
        val snapshot = scrollWatcher.snapshot(atBottom = atBottom, followBottom = followBottom)
        if (snapshot.isScrolling) {
            markdownState.pauseUiUpdates()
            anchor = anchor ?: scrollWatcher.captureAnchor()
            needsResume = true
            return
        }
        if (followBottom || snapshot.atBottom) requestFollowBottom()
    }

    /**
     * Coalesces content-commit follow requests into one scroll per frame.
     * Layout/height callbacks never call this method directly.
     */
    private fun requestFollowBottom() {
        if (!isStreaming || compensating) return
        if (followJob?.isActive == true) return
        followJob = followScope.launch {
            withFrameNanos { }
            withFrameNanos { }
            val snapshot = scrollWatcher.snapshot(atBottom = atBottom, followBottom = followBottom)
            if (!isStreaming || snapshot.isScrolling || (!followBottom && !snapshot.atBottom)) return@launch
            compensating = true
            try {
                val delta = bottomScrollDelta()
                if (delta > 0) {
                    scrollState.scroll(MutatePriority.Default) { scrollBy(delta.toFloat()) }
                }
                withFrameNanos { }
                atBottom = true
                followBottom = autoFollow
                anchor = null
                needsResume = false
            } finally {
                compensating = false
            }
        }
    }

    /** Kuikly rejects Float.MAX_VALUE as an invalid Int delta; use a finite bottom delta. */
    private fun bottomScrollDelta(): Int {
        if (scrollState !is LazyListState) return Int.MAX_VALUE / 4
        val info = scrollState.layoutInfo
        if (info.visibleItemsInfo.isEmpty()) return 0
        val viewportEnd = info.viewportEndOffset
        val last = info.visibleItemsInfo.lastOrNull() ?: return 0
        val visibleBottom = last.offset + last.size
        return (visibleBottom - viewportEnd)
            .coerceAtLeast(0)
            .coerceAtMost(info.viewportSize.height)
    }

    private suspend fun restoreAnchorOrBottom(toBottom: Boolean) {
        compensating = true
        try {
            val currentAnchor = anchor
            if (toBottom || (followBottom && (currentAnchor == null || atBottom))) {
                val delta = bottomScrollDelta()
                if (delta > 0) scrollState.scroll(MutatePriority.Default) { scrollBy(delta.toFloat()) }
            } else if (currentAnchor != null) {
                scrollWatcher.restore(currentAnchor)
            }
            atBottom = if (toBottom) true else
                scrollWatcher.snapshot(atBottom = atBottom, followBottom = followBottom).atBottom
            needsResume = false
        } finally {
            compensating = false
        }
    }

    internal fun markResumeNeeded() {
        needsResume = true
    }

}

internal data class ScrollSnapshot(
    val isScrolling: Boolean,
    val backward: Boolean,
    val atBottom: Boolean,
    val followBottom: Boolean,
    val bottomDistance: Int = Int.MAX_VALUE,
    val bottomThresholdPx: Int = DEFAULT_BOTTOM_THRESHOLD_PX,
) {
    val hold: Boolean get() = isScrolling
}

internal data class Anchor(
    val index: Int,
    val key: Any?,
    val offset: Int,
)

internal sealed class ScrollWatcher {
    abstract fun snapshot(atBottom: Boolean, followBottom: Boolean): ScrollSnapshot
    abstract fun captureAnchor(): Anchor
    abstract suspend fun restore(anchor: Anchor)

    class Lazy(
        private val state: LazyListState,
        private val bottomThresholdPx: Int,
    ) : ScrollWatcher() {
        override fun snapshot(atBottom: Boolean, followBottom: Boolean): ScrollSnapshot {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val bottomDistance = if (last != null && last.index == info.totalItemsCount - 1) {
                info.viewportEndOffset - (last.offset + last.size)
            } else Int.MAX_VALUE
            return ScrollSnapshot(
                isScrolling = state.isScrollInProgress,
                backward = state.lastScrolledBackward,
                atBottom = bottomDistance <= bottomThresholdPx,
                bottomDistance = bottomDistance,
                followBottom = followBottom,
                bottomThresholdPx = bottomThresholdPx,
            )
        }

        override fun captureAnchor(): Anchor {
            val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
            return Anchor(first?.index ?: state.firstVisibleItemIndex, first?.key, first?.offset ?: 0)
        }

        override suspend fun restore(anchor: Anchor) {
            if (anchor.index < 0 || anchor.index >= state.layoutInfo.totalItemsCount) return
            state.scrollToItem(anchor.index, anchor.offset)
            val after = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == anchor.index }
            val residual = if (after != null) after.offset - anchor.offset else 0
            if (residual != 0) {
                state.scroll(MutatePriority.Default) { scrollBy(residual.toFloat()) }
            }
        }
    }

    class Generic(private val state: ScrollableState) : ScrollWatcher() {
        override fun snapshot(atBottom: Boolean, followBottom: Boolean): ScrollSnapshot = ScrollSnapshot(
            isScrolling = state.isScrollInProgress,
            backward = state.lastScrolledBackward,
            atBottom = atBottom,
            followBottom = followBottom,
            bottomDistance = if (atBottom) 0 else Int.MAX_VALUE,
        )

        override fun captureAnchor(): Anchor = Anchor(0, null, 0)

        override suspend fun restore(anchor: Anchor) = Unit
    }

}

private const val DEFAULT_BOTTOM_THRESHOLD_PX = 96
