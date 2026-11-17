package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownStateImpl
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingLinkInvalidationTest {
    private fun state() = StreamingMarkdownStateImpl(
        lookupLinks = true,
        flavour = GFMFlavourDescriptor(),
        referenceLinkHandler = ReferenceLinkHandlerImpl(),
        diagnosticsEnabled = true,
    )

    @Test
    fun `late link definition only invalidates blocks using the changed label`() = runBlocking {
        val state = state()
        state.append("First [ref] paragraph.\n\nUnrelated paragraph.\n\n")
        val blocks = state.stableBlocks.value
        assertTrue(blocks.size >= 2)
        val linkedBlockId = blocks.first { it.source.contains("[ref]") }.id

        val before = state.blockLinkGenerations.value
        val cacheRevisionBefore = state.renderCache.revision
        state.append("Second paragraph.\n\n[ref]: https://example.com\n\n")
        val after = state.blockLinkGenerations.value

        assertEquals((before[linkedBlockId] ?: 0) + 1, after[linkedBlockId])
        assertTrue(
            blocks.filter { !it.source.contains("[ref]") }
                .all { (after[it.id] ?: 0) == (before[it.id] ?: 0) },
        )
        assertEquals(cacheRevisionBefore, state.renderCache.revision)
        assertEquals("https://example.com", state.referenceLinkHandler.find("[ref]"))
    }

    @Test
    fun `paused link changes still commit on finish`() = runBlocking {
        val state = state()
        state.append("Visible [late].\n\n")
        state.pauseUiUpdates()
        state.append("Tail text.\n\n[late]: https://example.org\n")
        val result = state.finish()

        assertTrue(result.finalized)
        assertEquals("https://example.org", state.referenceLinkHandler.find("[late]"))
        assertTrue(state.tailLinkGeneration.value > 0)
    }

    @Test
    fun `per block generation flow updates only affected blocks`() = runBlocking {
        val state = state()
        state.append("First [ref] paragraph.\n\nUnrelated paragraph.\n\n")
        val blocks = state.stableBlocks.value
        val linkedBlock = blocks.first { it.source.contains("[ref]") }
        val unrelatedBlock = blocks.first { !it.source.contains("[ref]") }
        val linkedFlow = state.blockLinkGeneration(linkedBlock.id)
        val unrelatedFlow = state.blockLinkGeneration(unrelatedBlock.id)

        state.append("Second paragraph.\n\n[ref]: https://example.com\n\n")

        assertTrue(linkedFlow.value > 0)
        assertEquals(0, unrelatedFlow.value)
        assertEquals(linkedFlow.value, state.blockLinkGenerations.value[linkedBlock.id])
    }

    @Test
    fun `late link definition keeps existing stable segment identity`() = runBlocking {
        val state = state()
        state.append("First [ref] paragraph.\n\nUnrelated paragraph.\n\n")
        val segmentsBefore = state.stableRenderSegments.value
        assertTrue(segmentsBefore.isNotEmpty())
        val originalSegment = segmentsBefore.first()

        state.append("Second paragraph.\n\n[ref]: https://example.com\n\n")

        val segmentsAfter = state.stableRenderSegments.value
        assertTrue(segmentsAfter.size >= segmentsBefore.size)
        assertEquals(originalSegment.id, segmentsAfter.first().id)
        assertEquals(originalSegment.blocks, segmentsAfter.first().blocks)
        assertEquals("https://example.com", state.referenceLinkHandler.find("[ref]"))
    }

    @Test
    fun `finish appends final stable blocks to render segments`() = runBlocking {
        val state = state()
        state.append("First paragraph.\n\nSecond paragraph.\n\nTail without closing blank line")
        val result = state.finish()

        assertTrue(result.finalized)
        assertEquals(
            state.stableBlocks.value.size,
            state.stableRenderSegments.value.sumOf { it.blocks.size },
        )
    }
}
