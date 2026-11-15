package com.tencent.kuiklybase.markdown

import com.tencent.kuikly.compose.ui.text.AnnotatedString
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuiklybase.markdown.annotator.AnnotatorSettings
import com.tencent.kuiklybase.markdown.model.MarkdownRenderCache
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MarkdownRenderCacheTest {

    @Test
    fun `same AST node and source reuses rendered text`() {
        val cache = MarkdownRenderCache()
        val node = FakeNode(0, 5)
        val style = TextStyle.Default
        val settings = FakeAnnotatorSettings
        var builds = 0

        val first = cache.getOrPut("hello", node, style, settings) {
            builds++
            AnnotatedString("first")
        }
        val second = cache.getOrPut("hello", node, style, settings) {
            builds++
            AnnotatedString("second")
        }

        assertSame(first, second)
        assertEquals(1, builds)
    }

    private data class FakeNode(
        override val startOffset: Int,
        override val endOffset: Int,
    ) : ASTNode {
        override val type = MarkdownElementTypes.PARAGRAPH
        override val parent: ASTNode? = null
        override val children: List<ASTNode> = emptyList()
    }

    private object FakeAnnotatorSettings : AnnotatorSettings {
        override val linkTextSpanStyle = com.tencent.kuikly.compose.ui.text.TextLinkStyles()
        override val codeSpanStyle = com.tencent.kuikly.compose.ui.text.SpanStyle()
        override val annotator = com.tencent.kuiklybase.markdown.model.DefaultMarkdownAnnotator(
            null,
            null,
            com.tencent.kuiklybase.markdown.model.DefaultMarkdownAnnotatorConfig(),
        )
        override val referenceLinkHandler = null
        override val linkInteractionListener = null
    }
}
