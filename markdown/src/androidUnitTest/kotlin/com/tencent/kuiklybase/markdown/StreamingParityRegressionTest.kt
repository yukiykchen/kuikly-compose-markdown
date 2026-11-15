package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownStateImpl
import com.tencent.kuiklybase.markdown.model.asRenderBlock
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StreamingParityRegressionTest {
    private fun describe(node: ASTNode): String =
        "${node.type.name}:${node.startOffset}-${node.endOffset}" +
            node.children.joinToString(prefix = "{", postfix = "}") { describe(it) }

    @Test
    fun `random chunks preserve full deep AST and block local source`() = runBlocking {
        val source = """
            # Unicode 中文 🌍

            **bold** *emphasis* ~~deleted~~ `inline` &amp; [doc][ref].

            > quoted paragraph
            >
            > - nested list

            - [x] done
            - [ ] pending
              - child **text**

            | A | B |
            | :- | -: |
            | link | **bold** |

            ```kotlin
            val text = "中文"
            ```

            [ref]: https://example.com
        """.trimIndent().replace("\n", "\r\n")
        val normalized = source.replace("\r\n", "\n")
        val expected = MarkdownParser(GFMFlavourDescriptor(), cancellationToken = CancellationToken.NonCancellable)
            .buildMarkdownTreeFromString(normalized as CharSequence).children
        repeat(12) { seed ->
            val state = StreamingMarkdownStateImpl(true, GFMFlavourDescriptor(), ReferenceLinkHandlerImpl())
            val random = Random(seed)
            var offset = 0
            while (offset < source.length) {
                val end = minOf(offset + random.nextInt(1, 65), source.length)
                state.append(source.substring(offset, end))
                if (random.nextBoolean()) state.append("")
                offset = end
            }
            val snapshot = state.finish()
            assertEquals(normalized, state.content.toString())
            assertEquals(expected.map(::describe), snapshot.blocks.map(::describe), "seed=$seed")
            expected.zip(snapshot.renderBlocks).forEach { (node, block) ->
                val local = node.asRenderBlock(normalized)
                assertEquals(local.source, block.source)
                assertEquals(describe(local.node), describe(block.node))
            }
            assertEquals("https://example.com", state.referenceLinkHandler.find("[ref]"))
        }
    }

    @Test
    fun `local source extraction never copies the entire document`() {
        val source = "# Heading\n\n" + "text\n\n".repeat(1000)
        val tree = MarkdownParser(GFMFlavourDescriptor(), cancellationToken = CancellationToken.NonCancellable)
            .buildMarkdownTreeFromString(source as CharSequence)
        val guarded = object : CharSequence {
            override val length = source.length
            override fun get(index: Int) = source[index]
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
                source.substring(startIndex, endIndex)
            override fun toString(): String = error("full document copy is forbidden")
        }
        assertEquals("# Heading", tree.children.first().asRenderBlock(guarded).nodeContent)
    }

    @Test
    fun `first reference wins and label whitespace is normalized`() = runBlocking {
        val state = StreamingMarkdownStateImpl(true, GFMFlavourDescriptor(), ReferenceLinkHandlerImpl())
        state.append("[Foo Bar]: https://first.example\n\n[foo   bar]: https://second.example\n\n")
        state.finish()
        assertEquals("https://first.example", state.referenceLinkHandler.find("[FOO\tBar]"))
        assertFalse(state.links.value.values.contains("https://second.example"))
    }

    @Test
    fun `reference destination uses parser normalization for brackets and entities`() = runBlocking {
        val state = StreamingMarkdownStateImpl(true, GFMFlavourDescriptor(), ReferenceLinkHandlerImpl())
        state.append("[ref]: <https://example.com/path?a=1&amp;b=2>\n\n")
        state.finish()
        assertEquals("https://example.com/path?a=1&b=2", state.referenceLinkHandler.find("[ref]"))
    }
}
