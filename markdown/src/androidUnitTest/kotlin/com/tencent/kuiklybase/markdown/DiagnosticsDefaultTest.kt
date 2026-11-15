package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownStateImpl
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticsDefaultTest {
    @Test
    fun `production state leaves diagnostics at defaults`() = runBlocking {
        val state = StreamingMarkdownStateImpl(
            lookupLinks = true,
            flavour = GFMFlavourDescriptor(),
            referenceLinkHandler = ReferenceLinkHandlerImpl(),
        )

        state.append("# Heading\n\nBody")
        state.finish()

        assertEquals(0, state.diagnostics.value.appendCount)
        assertEquals(0, state.diagnostics.value.stableBlockCount)
        assertEquals(0, state.diagnostics.value.reusedStableBlocks)
        assertEquals(0, state.recompositionCounters.stableBlockCompositions)
        assertEquals(true, state.stableBlocks.value.isNotEmpty())
    }
}
