package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownStateImpl
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamingUiCommitGateTest {
    private fun state() = StreamingMarkdownStateImpl(
        lookupLinks = true,
        flavour = GFMFlavourDescriptor(),
        referenceLinkHandler = ReferenceLinkHandlerImpl(),
        diagnosticsEnabled = true,
    )

    @Test
    fun `paused appends keep source but coalesce UI commits`() = runBlocking {
        val state = state()
        state.append("# Heading\n\nVisible paragraph.\n\n")
        val frozen = state.snapshot.value
        val frozenStable = state.stableBlocks.value
        val frozenTail = state.unstableBlocks.value
        val frozenLinksRevision = state.linksRevision.value

        state.pauseUiUpdates()
        state.append("A growing active tail.")
        state.append("\n\n# Second heading\n\n[ref]: https://example.com\n")

        assertEquals(frozen, state.snapshot.value)
        assertEquals(frozenStable, state.stableBlocks.value)
        assertEquals(frozenTail, state.unstableBlocks.value)
        assertEquals(frozenLinksRevision, state.linksRevision.value)
        assertEquals("", state.referenceLinkHandler.find("[ref]"))
        assertTrue(state.contentLength > frozen.renderBlocks.sumOf { it.source.length })
        assertFalse(state.uiUpdatesEnabled.value)

        val resumed = state.resumeUiUpdates()
        assertEquals(state.content.toString(), state.content.toString())
        assertEquals("https://example.com", state.referenceLinkHandler.find("[ref]"))
        assertTrue(resumed.renderBlocks.any { it.source.contains("Second heading") })
        assertTrue(state.uiUpdatesEnabled.value)
    }

    @Test
    fun `finish forces final commit while UI updates are paused`() = runBlocking {
        val state = state()
        state.append("# Heading\n\n")
        state.pauseUiUpdates()
        state.append("Final body with [ref].\n\n[ref]: https://example.com\n")
        val beforeFinish = state.snapshot.value

        val final = state.finish()

        assertTrue(beforeFinish !== final)
        assertTrue(final.finalized)
        assertEquals(final, state.snapshot.value)
        assertEquals("https://example.com", state.referenceLinkHandler.find("[ref]"))
        assertTrue(state.uiUpdatesEnabled.value)
        assertTrue(state.unstableBlocks.value.isEmpty())
    }

    @Test
    fun `reset clears paused gate and queued publication`() = runBlocking {
        val state = state()
        state.append("# Old\n\n")
        state.pauseUiUpdates()
        state.append("Queued but unpublished")
        state.reset()

        assertTrue(state.uiUpdatesEnabled.value)
        assertTrue(state.snapshot.value.blocks.isEmpty())
        assertEquals("", state.content.toString())
        state.append("# New\n\n")
        assertEquals("# New\n\n", state.content.toString())
        assertTrue(state.snapshot.value.blocks.isNotEmpty())
    }
}
