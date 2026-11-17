package com.tencent.kuiklybase.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.lazy.LazyListState
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.layout.onSizeChanged
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuiklybase.markdown.compose.components.MarkdownComponents
import com.tencent.kuiklybase.markdown.compose.components.markdownComponents
import com.tencent.kuiklybase.markdown.model.MarkdownRenderCache
import com.tencent.kuiklybase.markdown.model.MarkdownRenderBlock
import com.tencent.kuiklybase.markdown.model.MarkdownRenderSegment
import com.tencent.kuiklybase.markdown.model.RecompositionCounters
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownState
import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl

/**
 * Renders an active stream inside a LazyColumn.
 *
 * This is retained for compatibility and bounded hosts. Chat UIs should use
 * [StreamingMarkdownContent] inside a message-level list instead: dynamic
 * block counts and heights make per-block virtualization prone to blank cells.
 */
@Composable
@Deprecated(
    "Use StreamingMarkdownContent inside a message-level LazyColumn for active streams.",
    ReplaceWith("StreamingMarkdownContent(streamingMarkdownState, components)"),
)
fun LazyMarkdownSuccess(
    streamingMarkdownState: StreamingMarkdownState,
    components: MarkdownComponents = remember { markdownComponents() },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    precomposeAll: Boolean = false,
    singleItem: Boolean = false,
) {
    StreamingMarkdownBlocks(
        streamingMarkdownState = streamingMarkdownState,
        components = components,
        modifier = modifier,
        contentPadding = contentPadding,
        precomposeAll = precomposeAll,
        singleItem = singleItem,
    )
}

/** Renders exactly the captured snapshot, even after the live stream advances. */
@Composable
fun LazyMarkdownSuccess(
    streamingMarkdownState: StreamingMarkdownState,
    snapshot: StreamingMarkdownState.Snapshot,
    components: MarkdownComponents = remember { markdownComponents() },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    precomposeAll: Boolean = false,
    singleItem: Boolean = false,
    state: LazyListState? = null,
) {
    require(snapshot.blocks.isEmpty() || snapshot.renderBlocks.isNotEmpty()) {
        "Use a snapshot returned by StreamingMarkdownState.append() or finish()"
    }
    val handler = remember(snapshot) {
        ReferenceLinkHandlerImpl().also { handler ->
            snapshot.referenceDefinitions.forEach { (key, value) -> handler.store(key, value) }
        }
    }
    CompositionLocalProvider(
        LocalReferenceLinkHandler provides handler,
        LocalMarkdownRenderCache provides remember(snapshot) { MarkdownRenderCache() },
        LocalMarkdownComponents provides components,
    ) {
        LazyColumn(
            modifier = modifier,
            state = state ?: rememberLazyListState(),
            contentPadding = contentPadding,
            beyondBoundsItemCount = if (precomposeAll) snapshot.renderBlocks.size else 3,
        ) {
            if (singleItem) {
                item(key = "markdown-snapshot", contentType = "markdown-snapshot") {
                    Column {
                        snapshot.renderBlocks.forEach { block ->
                            key(block.id) { RenderMarkdownBlock(block, components) }
                        }
                    }
                }
            } else {
                items(snapshot.renderBlocks, key = { it.id }, contentType = { it.type.name }) { block ->
                    RenderMarkdownBlock(block, components)
                }
            }
        }
    }
}

/**
 * Streaming Markdown content with separate stable and active-tail scopes.
 * This is the recommended entry point for chat bubbles and other non-lazy hosts.
 */
@Composable
fun StreamingMarkdownContent(
    streamingMarkdownState: StreamingMarkdownState,
    components: MarkdownComponents = remember { markdownComponents() },
    diagnosticsEnabled: Boolean = false,
    emojiShortcodes: com.tencent.kuiklybase.markdown.model.MarkdownEmojiShortcodes? =
        LocalMarkdownEmojiShortcodes.current,
) {
    CompositionLocalProvider(
        LocalReferenceLinkHandler provides streamingMarkdownState.referenceLinkHandler,
        LocalMarkdownRenderCache provides streamingMarkdownState.renderCache,
        LocalMarkdownComponents provides components,
        LocalMarkdownEmojiShortcodes provides emojiShortcodes,
    ) {
        Column {
            key("stable-markdown-host") {
                StableMarkdownHost(
                    streamingMarkdownState = streamingMarkdownState,
                    components = components,
                    diagnosticsEnabled = diagnosticsEnabled,
                )
            }
            key("active-tail-host") {
                ActiveTailBlocks(
                    streamingMarkdownState = streamingMarkdownState,
                    components = components,
                    diagnosticsEnabled = diagnosticsEnabled,
                )
            }
        }
    }
}

/** Non-scrolling content for an immutable answer retained in a chat history. */
@Composable
fun MarkdownSnapshotContent(
    snapshot: StreamingMarkdownState.Snapshot,
    components: MarkdownComponents = remember { markdownComponents() },
    emojiShortcodes: com.tencent.kuiklybase.markdown.model.MarkdownEmojiShortcodes? =
        LocalMarkdownEmojiShortcodes.current,
) {
    require(snapshot.blocks.isEmpty() || snapshot.renderBlocks.isNotEmpty()) {
        "Use a snapshot returned by StreamingMarkdownState.append() or finish()"
    }
    val handler = remember(snapshot) {
        ReferenceLinkHandlerImpl().also { handler ->
            snapshot.referenceDefinitions.forEach { (key, value) -> handler.store(key, value) }
        }
    }
    CompositionLocalProvider(
        LocalReferenceLinkHandler provides handler,
        LocalMarkdownRenderCache provides remember(snapshot) { MarkdownRenderCache() },
        LocalMarkdownComponents provides components,
        LocalMarkdownEmojiShortcodes provides emojiShortcodes,
    ) {
        Column {
            snapshot.renderBlocks.forEach { block ->
                key(block.id) { RenderMarkdownBlock(block, components) }
            }
        }
    }
}

@Composable
private fun StableMarkdownHost(
    streamingMarkdownState: StreamingMarkdownState,
    components: MarkdownComponents,
    diagnosticsEnabled: Boolean,
) {
    val stableRenderSegments by streamingMarkdownState.stableRenderSegments.collectAsState()
    StableMarkdownBlocks(
        segments = stableRenderSegments,
        blockCount = stableRenderSegments.sumOf { it.blocks.size },
        components = components,
        blockLinkGeneration = streamingMarkdownState::blockLinkGeneration,
        renderCache = streamingMarkdownState.renderCache,
        counters = streamingMarkdownState.recompositionCounters,
        countBlocks = diagnosticsEnabled,
        diagnosticsEnabled = diagnosticsEnabled,
    )
}

@Composable
private fun ActiveTailBlocks(
    streamingMarkdownState: StreamingMarkdownState,
    components: MarkdownComponents,
    diagnosticsEnabled: Boolean,
) {
    val blocks by streamingMarkdownState.unstableBlocks.collectAsState()
    val tailLinkGeneration by streamingMarkdownState.tailLinkGeneration.collectAsState()
    val counters = streamingMarkdownState.recompositionCounters
    key(tailLinkGeneration) {
        if (diagnosticsEnabled) SideEffect { counters.recordActiveTail() }
        Column {
            blocks.forEach { block ->
                key(block.id) {
                    val linkGeneration by streamingMarkdownState.blockLinkGeneration(block.id).collectAsState()
                    CompositionLocalProvider(
                        LocalMarkdownBlockLinkGeneration provides linkGeneration,
                    ) {
                        if (diagnosticsEnabled) SideEffect { counters.recordActiveTailBlock() }
                        RenderMarkdownBlock(block, components)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingMarkdownBlocks(
    streamingMarkdownState: StreamingMarkdownState,
    components: MarkdownComponents,
    modifier: Modifier,
    contentPadding: PaddingValues,
    precomposeAll: Boolean,
    singleItem: Boolean,
) {
    val stableBlocks by streamingMarkdownState.stableBlocks.collectAsState()
    val stableRenderSegments by streamingMarkdownState.stableRenderSegments.collectAsState()
    val unstableBlocks by streamingMarkdownState.unstableBlocks.collectAsState()
    val rememberedComponents = components

    CompositionLocalProvider(
        LocalMarkdownRenderCache provides streamingMarkdownState.renderCache,
        LocalReferenceLinkHandler provides streamingMarkdownState.referenceLinkHandler,
        LocalMarkdownComponents provides components,
    ) {
        if (singleItem) {
            LazyColumn(modifier = modifier, contentPadding = contentPadding) {
                item(key = "markdown-document", contentType = "markdown-document") {
                    Column {
                        IncrementalMarkdownDocument(
                            streamingMarkdownState = streamingMarkdownState,
                            stableBlocks = stableBlocks,
                            stableRenderSegments = stableRenderSegments,
                            unstableBlocks = unstableBlocks,
                            components = rememberedComponents,
                            counters = streamingMarkdownState.recompositionCounters,
                            countBlocks = false,
                            diagnosticsEnabled = false,
                        )
                    }
                }
            }
            return@CompositionLocalProvider
        }

        LazyColumn(
            modifier = modifier,
            contentPadding = contentPadding,
            beyondBoundsItemCount = if (precomposeAll) stableBlocks.size + unstableBlocks.size else 3,
        ) {
            items(
                items = stableBlocks,
                key = { block -> block.id },
                contentType = { block -> block.type.name },
            ) { block ->
                RenderMarkdownBlock(block, rememberedComponents)
            }
            items(
                items = unstableBlocks,
                key = { block -> block.id },
                contentType = { block -> block.type.name },
            ) { block ->
                RenderMarkdownBlock(block, rememberedComponents)
            }
        }
    }
}

/**
 * Stable and changing Markdown live in separate composition scopes. A new
 * unstable tail does not invalidate the stable subtree's composition groups.
 */
@Composable
private fun IncrementalMarkdownDocument(
    streamingMarkdownState: StreamingMarkdownState,
    stableBlocks: List<MarkdownRenderBlock>,
    stableRenderSegments: List<MarkdownRenderSegment>,
    unstableBlocks: List<MarkdownRenderBlock>,
    components: MarkdownComponents,
    counters: RecompositionCounters,
    countBlocks: Boolean,
    diagnosticsEnabled: Boolean,
) {
    key("stable-markdown") {
        StableMarkdownBlocks(
            segments = stableRenderSegments,
            blockCount = stableRenderSegments.sumOf { it.blocks.size },
            components = components,
            blockLinkGeneration = streamingMarkdownState::blockLinkGeneration,
            renderCache = streamingMarkdownState.renderCache,
            counters = counters,
            countBlocks = countBlocks,
            diagnosticsEnabled = diagnosticsEnabled,
        )
    }
    key("active-tail") {
        if (diagnosticsEnabled) SideEffect { counters.recordActiveTail() }
        Column {
            unstableBlocks.forEach { block ->
                key(block.id) {
                    if (diagnosticsEnabled && countBlocks) {
                        SideEffect { counters.recordActiveTailBlock() }
                    }
                    RenderMarkdownBlock(block, components)
                }
            }
        }
    }
}

@Composable
private fun StableMarkdownBlocks(
    segments: List<MarkdownRenderSegment>,
    blockCount: Int,
    components: MarkdownComponents,
    blockLinkGeneration: (Long) -> kotlinx.coroutines.flow.StateFlow<Int>,
    renderCache: MarkdownRenderCache?,
    counters: RecompositionCounters,
    countBlocks: Boolean,
    diagnosticsEnabled: Boolean,
) {
    if (diagnosticsEnabled) SideEffect { counters.recordStableRegion() }
    val columnModifier = if (diagnosticsEnabled) {
        Modifier.onSizeChanged { size ->
            if (blockCount > 0) {
                renderCache?.rememberMeasuredHeight(-1L, size.height)
            }
        }
    } else {
        Modifier
    }
    Column(modifier = columnModifier) {
        segments.forEach { segment ->
            key(segment.id) {
                StableRenderSegmentContent(
                    segment = segment,
                    components = components,
                    counters = counters,
                    blockLinkGeneration = blockLinkGeneration,
                    countBlocks = countBlocks && diagnosticsEnabled,
                )
            }
        }
    }
}

@Composable
private fun StableRenderSegmentContent(
    segment: MarkdownRenderSegment,
    components: MarkdownComponents,
    counters: RecompositionCounters,
    blockLinkGeneration: (Long) -> kotlinx.coroutines.flow.StateFlow<Int>,
    countBlocks: Boolean,
) {
    if (countBlocks) {
        SideEffect { repeat(segment.blocks.size) { counters.recordStableBlock() } }
    }
    RenderMarkdownSegment(segment, components, blockLinkGeneration)
}

@Composable
private fun RenderMarkdownSegment(
    segment: MarkdownRenderSegment,
    components: MarkdownComponents,
    blockLinkGeneration: (Long) -> kotlinx.coroutines.flow.StateFlow<Int>,
) {
    Column {
        segment.blocks.forEach { block ->
            val linkGeneration by blockLinkGeneration(block.id).collectAsState()
            key(block.id, linkGeneration) {
                CompositionLocalProvider(
                    LocalMarkdownBlockLinkGeneration provides linkGeneration,
                ) {
                    RenderMarkdownBlock(block, components)
                }
            }
        }
    }
}

@Composable
internal fun RenderMarkdownBlock(
    block: MarkdownRenderBlock,
    components: MarkdownComponents,
) {
    handleElement(
        node = block.node,
        components = components,
        content = block.source,
        includeSpacer = false,
        skipLinkDefinition = true,
    )
}
