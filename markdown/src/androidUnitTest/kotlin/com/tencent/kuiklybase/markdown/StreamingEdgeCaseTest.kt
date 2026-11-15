package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownStateImpl
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.ast.ASTNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingEdgeCaseTest {

    private fun state() = StreamingMarkdownStateImpl(
        lookupLinks = true,
        flavour = GFMFlavourDescriptor(),
        referenceLinkHandler = ReferenceLinkHandlerImpl(),
        diagnosticsEnabled = true,
    )

    private fun describe(node: ASTNode): String =
        "${node.type.name}:${node.startOffset}-${node.endOffset}" +
            node.children.joinToString(prefix = "{", postfix = "}") { describe(it) }

    private fun describeSource(source: String): List<String> {
        val tree = MarkdownParser(
            GFMFlavourDescriptor(),
            cancellationToken = CancellationToken.NonCancellable,
        ).buildMarkdownTreeFromString(source as CharSequence)
        return tree.children.map(::describe)
    }

    private fun assertStreamingMatchesFullParse(source: String, chunkSize: Int) = runBlocking {
        val state = state()
        source.chunked(chunkSize).forEach { state.append(it) }
        val snapshot = state.finish()
        assertTrue(snapshot.finalized)
        assertTrue(state.diagnostics.value.finalParseMatches)
        assertEquals(source, state.content.toString())
        assertEquals(describeSource(source), snapshot.blocks.map(::describe))
    }

    @Test
    fun `long unclosed code fence remains parseable`() {
        val source = "# Head\n\n```kotlin\n" + "val x = 1\n".repeat(400)
        assertStreamingMatchesFullParse(source, 17)
    }

    @Test
    fun `large GFM table remains parseable`() {
        val source = buildString {
            append("| A | B |\n| - | - |\n")
            repeat(250) { row -> append("| $row | value-$row |\n") }
        }
        assertStreamingMatchesFullParse(source, 31)
    }

    @Test
    fun `late link definition keeps final parse consistent`() {
        val source = "Paragraph with [doc][ref].\n\n" + ("text ".repeat(80)) + "\n\n[ref]: https://example.com\n"
        assertStreamingMatchesFullParse(source, 13)
    }
}
