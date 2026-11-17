package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.MarkdownRenderSegmentBudget
import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownStateImpl
import com.tencent.kuiklybase.markdown.model.asRenderSegments
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownRenderSegmentTest {
    private fun state() = StreamingMarkdownStateImpl(
        lookupLinks = true,
        flavour = GFMFlavourDescriptor(),
        referenceLinkHandler = ReferenceLinkHandlerImpl(),
    )

    @Test
    fun `short blocks share deterministic bounded segments`() {
        val source = (1..20).joinToString("\n\n") { "Paragraph $it" }
        val blocks = runBlocking {
            val state = state()
            state.append(source)
            state.finish()
            state.stableBlocks.value
        }
        val segments = blocks.asRenderSegments(MarkdownRenderSegmentBudget(maxCharacters = 40, maxBlocks = 3))
        assertTrue(segments.size > 1)
        assertTrue(segments.all { it.blocks.size <= 3 })
        assertEquals(segments, blocks.asRenderSegments(MarkdownRenderSegmentBudget(40, 3)))
        assertEquals(blocks, segments.flatMap { it.blocks })
    }

    @Test
    fun `heavy syntax blocks are isolated`() {
        val source = """
            Short intro.

            ```kotlin
            val x = 1
            ```

            | A | B |
            | - | - |
            | 1 | 2 |

            Short outro.
        """.trimIndent()
        val blocks = runBlocking {
            val state = state()
            state.append(source)
            state.finish()
            state.stableBlocks.value
        }
        val segments = blocks.asRenderSegments()
        val heavy = blocks.filter {
            it.type.name == "CODE_FENCE" || it.type.name == "TABLE"
        }
        assertTrue(heavy.isNotEmpty())
        heavy.forEach { block ->
            assertTrue(
                segments.any { it.isolated && it.blocks.size == 1 && it.blocks.first().id == block.id },
                "heavy block ${block.type.name} must be isolated",
            )
        }
    }

    @Test
    fun `streaming finish retains segment identities`() = runBlocking {
        val state = state()
        state.append("# Heading\n\nFirst paragraph.\n\n")
        val before = state.stableRenderSegments.value
        assertTrue(before.isNotEmpty())
        val final = state.finish()
        assertTrue(final.finalized)
        val after = state.stableRenderSegments.value
        assertEquals(before.first().id, after.first().id)
        assertTrue(before.first().blocks.all { block -> after.flatMap { it.blocks }.any { it.id == block.id } })
    }
}
