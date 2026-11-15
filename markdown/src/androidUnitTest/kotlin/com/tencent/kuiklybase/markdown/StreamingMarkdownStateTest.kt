package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.Input
import com.tencent.kuiklybase.markdown.model.MarkdownState
import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamingMarkdownStateTest {

    private val sample = """
        # Streaming heading

        First paragraph with **bold** and a [link][ref].

        - item one
        - item two
          - nested item

        | A | B |
        | - | - |
        | 1 | 2 |

        ```kotlin
        val answer = 42
        ```

        [ref]: https://example.com
    """.trimIndent()

    @Test
    fun `streaming final AST matches full parse for every chunk size`() = runBlocking {
        val flavour = GFMFlavourDescriptor()
        val expected = newState(flavour)
        expected.parse(sample.replace("\r\n", "\n"), immediate = true)

        listOf(1, 2, 3, 7, 64, sample.length).forEach { chunkSize ->
            val state = newState(flavour)
            state.startStream()
            var offset = 0
            while (offset < sample.length) {
                val end = minOf(offset + chunkSize, sample.length)
                state.append(sample.substring(offset, end), immediate = true)
                offset = end
            }
            state.finishStream(immediate = true)

            assertEquals(
                expected.blocks.describe(),
                state.blocks.describe(),
                "final AST differs for chunk size $chunkSize",
            )
        }
    }

    @Test
    fun `CRLF split across chunks is normalized to one LF`() = runBlocking {
        val source = "first\r\nsecond\r\n"
        val state = newState(GFMFlavourDescriptor())
        state.startStream()
        source.forEach { state.append(it.toString(), immediate = true) }
        state.finishStream(immediate = true)

        assertEquals(source.replace("\r\n", "\n"), state.getContent())
        val expected = newState(GFMFlavourDescriptor())
        expected.parse(source.replace("\r\n", "\n"), immediate = true)
        assertEquals(expected.blocks.describe(), state.blocks.describe())
    }

    @Test
    fun `append freezes stable AST nodes`() = runBlocking {
        val state = newState(GFMFlavourDescriptor())
        state.startStream()
        state.append("# Heading\n\n", immediate = true)
        val heading = state.blocks.firstOrNull { it.type.name == MarkdownElementTypes.ATX_1.name }

        state.append("A growing paragraph.", immediate = true)
        val headingAfterAppend = state.blocks.firstOrNull {
            it.type.name == MarkdownElementTypes.ATX_1.name
        }

        assertEquals(true, heading !== null)
        assertEquals(true, heading === headingAfterAppend)
    }

    @Test
    fun `concurrent appends retain every chunk`() = runBlocking {
        val chunks = listOf("# ", "Head", "ing\n\n", "tail")
        val state = newState(GFMFlavourDescriptor())
        state.startStream()
        chunks.map { chunk ->
            async { state.append(chunk) }
        }.awaitAll()
        state.finishStream()

        val content = state.getContent()
        assertEquals(chunks.sumOf { it.length }, content.length)
        chunks.forEach { chunk ->
            assertEquals(true, content.contains(chunk.trim()))
        }
    }

    private fun newState(flavour: GFMFlavourDescriptor): MarkdownState = MarkdownState(
        Input(
            content = "",
            shareModel = false,
            lookupLinks = true,
            flavour = flavour,
            parser = MarkdownParser(flavour, cancellationToken = CancellationToken.NonCancellable),
            referenceLinkHandler = ReferenceLinkHandlerImpl(),
        )
    )

    private fun List<ASTNode>.describe(): String = joinToString("\n") { node ->
        fun ASTNode.tree(): String = buildString {
            append(type.name)
            append('[')
            append(startOffset)
            append('-')
            append(endOffset)
            append(']')
            if (children.isNotEmpty()) {
                append('{')
                append(children.joinToString(",") { it.tree() })
                append('}')
            }
        }
        node.tree()
    }
}
