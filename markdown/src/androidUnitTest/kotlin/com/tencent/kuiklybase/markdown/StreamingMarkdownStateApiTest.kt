package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownState
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownStateImpl
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingMarkdownStateApiTest {

    private fun state(
        lookupLinks: Boolean = true,
        handler: ReferenceLinkHandlerImpl = ReferenceLinkHandlerImpl(),
    ): Pair<StreamingMarkdownState, ReferenceLinkHandlerImpl> =
        StreamingMarkdownStateImpl(lookupLinks, GFMFlavourDescriptor(), handler) to handler

    @Test
    fun `snapshot exposes stable blocks and unstable tail`() = runBlocking {
        val (state, _) = state()
        val snapshot = state.append(
            """
                # Heading

                A stable paragraph.

                """.trimIndent() + "\n\n" + "Live tail"
        )

        assertTrue(snapshot.stableAst.isNotEmpty())
        assertTrue(snapshot.stableAst.any { it.type.name == MarkdownElementTypes.ATX_1.name })
        assertTrue(snapshot.unstableAstTail.isNotEmpty())
        assertEquals(snapshot, state.snapshot.value)
    }

    @Test
    fun `stable AST identity is preserved across appends`() = runBlocking {
        val (state, _) = state()
        val first = state.append("# Heading\n\nFirst paragraph.\n\n")
        val stableHeading = first.stableAst.first()
        val second = state.append("Tail paragraph.")

        assertTrue(second.stableAst.any { it === stableHeading })
        assertTrue(second.blocks.size >= first.blocks.size)
    }

    @Test
    fun `CRLF split across chunks is normalized once`() = runBlocking {
        val (state, _) = state()
        "first\r".forEach { state.append(it.toString()) }
        state.append("\nsecond\r\n")
        val snapshot = state.finish()

        assertEquals("first\nsecond\n", state.content.toString())
        assertTrue(snapshot.blocks.isNotEmpty())
    }

    @Test
    fun `streaming snapshot matches full parse after finish`() = runBlocking {
        val source = """
            # Heading

            Paragraph with **bold**.

            - item one
            - item two

            | A | B |
            | - | - |
            | 1 | 2 |

            ```kotlin
            val answer = 42
            ```

            [ref]: https://example.com
        """.trimIndent()
        val (state, _) = state()
        source.chunked(7).forEach { state.append(it) }
        val snapshot = state.finish()
        val expected = MarkdownParser(
            GFMFlavourDescriptor(),
            cancellationToken = org.intellij.markdown.parser.CancellationToken.NonCancellable,
        ).buildMarkdownTreeFromString(state.content.toString() as CharSequence).children

        fun describe(nodes: List<org.intellij.markdown.ast.ASTNode>): String =
            nodes.joinToString(";") { "${it.type.name}:${it.startOffset}-${it.endOffset}" }

        assertEquals(describe(expected), describe(snapshot.blocks))
    }
}
