package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownStateImpl
import com.tencent.kuiklybase.markdown.model.asRenderBlock
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IncrementalStreamingRenderTest {

    private fun state() = StreamingMarkdownStateImpl(
        lookupLinks = true,
        flavour = GFMFlavourDescriptor(),
        referenceLinkHandler = ReferenceLinkHandlerImpl(),
        diagnosticsEnabled = true,
    )

    @Test
    fun `stable render blocks are reused while active tail grows`() = runBlocking {
        val state = state()
        state.append("# Heading\n\nFirst paragraph.\n\n")
        val stableHeading = state.stableBlocks.value.first { it.type == MarkdownElementTypes.ATX_1 }
        val stableListRef = state.stableBlocks.value

        val tailBefore = state.unstableBlocks.value
        state.append("Tail paragraph.")
        val tailAfter = state.unstableBlocks.value

        assertSame(stableHeading, state.stableBlocks.value.first { it.id == stableHeading.id })
        assertSame(stableListRef, state.stableBlocks.value)
        assertTrue(tailBefore !== tailAfter)
        assertTrue(tailAfter.isNotEmpty())
        assertTrue(state.diagnostics.value.reusedStableBlocks >= 1)
    }

    @Test
    fun `finish publishes full parse blocks and records parity`() = runBlocking {
        val source = """
            # Heading

            Paragraph with [ref].

            - one
            - two

            | A | B |
            | - | - |
            | 1 | 2 |

            [ref]: https://example.com
        """.trimIndent()
        val state = state()
        source.chunked(7).forEach { state.append(it) }
        val finalSnapshot = state.finish()

        val expected = MarkdownParser(
            GFMFlavourDescriptor(),
            cancellationToken = CancellationToken.NonCancellable,
        ).buildMarkdownTreeFromString(source as CharSequence).children
        assertEquals(expected.size, state.stableBlocks.value.size)
        assertTrue(state.unstableBlocks.value.isEmpty())
        assertTrue(finalSnapshot.finalized)
        assertTrue(state.diagnostics.value.finalParseMatches)
        assertTrue(state.links.value.values.contains("https://example.com"))
    }

    @Test
    fun `render block uses local source and rebased AST offsets`() {
        val source = "prefix\n\n# Heading\n\ntail"
        val tree = MarkdownParser(
            GFMFlavourDescriptor(),
            cancellationToken = CancellationToken.NonCancellable,
        ).buildMarkdownTreeFromString(source as CharSequence)
        val heading = tree.children.first { it.type == MarkdownElementTypes.ATX_1 }
        val block = heading.asRenderBlock(source)

        assertEquals("# Heading", block.nodeContent)
        assertEquals(
            block.nodeContent,
            block.source.substring(block.node.startOffset, block.node.endOffset),
        )
        assertTrue(block.node.startOffset >= 0)
        assertTrue(block.node.endOffset <= block.source.length)
        assertTrue(block.node.children.isNotEmpty())
    }
}
