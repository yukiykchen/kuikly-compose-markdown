package com.tencent.kuiklybase.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.layout.onSizeChanged
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuiklybase.markdown.compose.components.MarkdownComponents
import com.tencent.kuiklybase.markdown.compose.components.markdownComponents
import com.tencent.kuiklybase.markdown.model.MarkdownRenderCache
import com.tencent.kuiklybase.markdown.model.MarkdownRenderBlock
import com.tencent.kuiklybase.markdown.model.RecompositionCounters
import com.tencent.kuiklybase.markdown.model.StableBlockSlot
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownState
import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl

/**
 * Renders a streaming Markdown snapshot in a LazyColumn. Only visible blocks
 * are composed, and stable keys/content types let the lazy layout reuse items.
 */
@Composable
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
            contentPadding = contentPadding,
            beyondBoundsItemCount = if (precomposeAll) snapshot.renderBlocks.size else 1,
        ) {
            if (singleItem) {
                item(key = "markdown-snapshot") {
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
) {
    val unstableBlocks by streamingMarkdownState.unstableBlocks.collectAsState()
    CompositionLocalProvider(
        LocalReferenceLinkHandler provides streamingMarkdownState.referenceLinkHandler,
        LocalMarkdownRenderCache provides streamingMarkdownState.renderCache,
        LocalMarkdownComponents provides components,
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
                    blocks = unstableBlocks,
                    components = components,
                    counters = streamingMarkdownState.recompositionCounters,
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
    val stableBlockSlots by streamingMarkdownState.stableBlockSlots.collectAsState()
    val linksRevision by streamingMarkdownState.linksRevision.collectAsState()
    StableMarkdownBlocks(
        slots = stableBlockSlots,
        blockCount = stableBlockSlots.size,
        components = components,
        linksRevision = linksRevision,
        renderCache = streamingMarkdownState.renderCache,
        counters = streamingMarkdownState.recompositionCounters,
        countBlocks = diagnosticsEnabled,
        diagnosticsEnabled = diagnosticsEnabled,
    )
}

@Composable
private fun ActiveTailBlocks(
    blocks: List<MarkdownRenderBlock>,
    components: MarkdownComponents,
    counters: RecompositionCounters,
    diagnosticsEnabled: Boolean,
) {
    if (diagnosticsEnabled) SideEffect { counters.recordActiveTail() }
    Column {
        blocks.forEach { block ->
            key(block.id) {
                if (diagnosticsEnabled) SideEffect { counters.recordActiveTailBlock() }
                RenderMarkdownBlock(block, components)
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
    val stableBlockSlots by streamingMarkdownState.stableBlockSlots.collectAsState()
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
                            stableBlockSlots = stableBlockSlots,
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
            beyondBoundsItemCount = if (precomposeAll) stableBlocks.size + unstableBlocks.size else 1,
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
                key = { block -> "tail-${block.id}" },
                contentType = { block -> "tail-${block.type.name}" },
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
    stableBlockSlots: List<StableBlockSlot>,
    unstableBlocks: List<MarkdownRenderBlock>,
    components: MarkdownComponents,
    counters: RecompositionCounters,
    countBlocks: Boolean,
    diagnosticsEnabled: Boolean,
) {
    val linksRevision by streamingMarkdownState.linksRevision.collectAsState()
    key("stable-markdown") {
        StableMarkdownBlocks(
            slots = stableBlockSlots,
            blockCount = stableBlocks.size,
            components = components,
            linksRevision = linksRevision,
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
    slots: List<StableBlockSlot>,
    blockCount: Int,
    components: MarkdownComponents,
    linksRevision: Int,
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
        slots.forEach { slot ->
            key(slot.id, linksRevision) {
                StableBlockSlotContent(slot, components, counters, countBlocks && diagnosticsEnabled)
            }
        }
    }
}

@Composable
private fun StableBlockSlotContent(
    slot: StableBlockSlot,
    components: MarkdownComponents,
    counters: RecompositionCounters,
    countBlocks: Boolean,
) {
    val movableBlock = remember(slot, components, counters, countBlocks) {
        movableContentOf {
            if (countBlocks) {
                SideEffect { counters.recordStableBlock() }
            }
            RenderMarkdownBlock(slot.value, components)
        }
    }
    movableBlock()
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
