package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownStateImpl
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.promise
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingJsRegressionTest {
    private fun describe(node: ASTNode): String = "${node.type.name}:${node.startOffset}-${node.endOffset}" +
        node.children.joinToString(prefix = "{", postfix = "}") { describe(it) }

    @Test
    fun unicodeAndReferencesAcrossOneCharacterChunks() = MainScope().promise {
        val source = "# 中文 🌍\n\nParagraph **bold** and [ref].\n\n| A | B |\n| - | - |\n| 1 | 2 |\n\n" +
            "```kotlin\nval x = 1\n```\n\n[ref]: https://example.com\n"
        val state = StreamingMarkdownStateImpl(true, GFMFlavourDescriptor(), ReferenceLinkHandlerImpl())
        source.forEach { state.append(it.toString()) }
        val final = state.finish()
        val expected = MarkdownParser(GFMFlavourDescriptor(), cancellationToken = CancellationToken.NonCancellable)
            .buildMarkdownTreeFromString(source as CharSequence)
        assertEquals(expected.children.map(::describe), final.blocks.map(::describe))
        assertEquals("https://example.com", state.referenceLinkHandler.find("[ref]"))
        state.reset()
        assertEquals("", state.referenceLinkHandler.find("[ref]"))
        assertTrue(state.snapshot.value.blocks.isEmpty())
    }
}
