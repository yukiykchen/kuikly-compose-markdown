package com.tencent.kuiklybase.markdown

import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.AnnotatedString
import com.tencent.kuikly.compose.ui.text.SpanStyle
import com.tencent.kuikly.compose.ui.text.TextLinkStyles
import com.tencent.kuikly.compose.ui.text.buildAnnotatedString
import com.tencent.kuiklybase.markdown.annotator.AnnotatorSettings
import com.tencent.kuiklybase.markdown.annotator.buildMarkdownAnnotatedString
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownAnnotator
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownAnnotatorConfig
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownCodeHighlighter
import com.tencent.kuiklybase.markdown.model.MarkdownCodeHighlight
import com.tencent.kuiklybase.markdown.model.MarkdownCodeHighlightColors
import com.tencent.kuiklybase.markdown.model.MarkdownAlertType
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownColors
import com.tencent.kuiklybase.markdown.model.findAlertType
import com.tencent.kuiklybase.markdown.compose.elements.splitMarkdownCodeChunks
import com.tencent.kuiklybase.markdown.compose.extendedspans.ExtendedSpans
import com.tencent.kuiklybase.markdown.model.buildHighlightedCodeString
import com.tencent.kuiklybase.markdown.model.sliceForChunk
import com.tencent.kuiklybase.markdown.utils.highlightSpanStyle
import com.tencent.kuiklybase.markdown.utils.resolveImageAlt
import com.tencent.kuiklybase.markdown.utils.resolveImageLink
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HighlightRenderingTest {
    @Test
    fun `GFM highlight keeps inner text and strips paired delimiters`() {
        val source = "Hello ==**重点**== world"
        val text = renderInline(source, SpanStyle(background = Color.Yellow))
        assertEquals("Hello 重点 world", text.text)
        assertTrue(text.spanStyles.any { it.item.background == Color.Yellow })
        assertTrue(text.spanStyles.any { it.item.fontWeight?.weight == 700 })
    }

    @Test
    fun `default highlight uses lemon yellow with readable foreground`() {
        val colors = DefaultMarkdownColors()
        assertEquals(Color(0xFFFFF44F), colors.highlightBackground)
        assertEquals(Color(0xFF202124), colors.highlightText)
        assertEquals(colors.highlightBackground, colors.highlightSpanStyle.background)
        assertEquals(colors.highlightText, colors.highlightSpanStyle.color)
    }

    @Test
    fun `default theme keeps native highlight background after extended span preparation`() {
        val colors = DefaultMarkdownColors()
        val source = "Hello ==重点== world"
        val text = renderInline(source, colors.highlightSpanStyle)

        // The default host has no painters, so Kuikly 2.30+ receives the span
        // background and maps it to the native RichText backgroundColor prop.
        val prepared = ExtendedSpans().extend(text)
        assertEquals("Hello 重点 world", prepared.text)
        assertEquals(
            colors.highlightBackground,
            prepared.spanStyles.first { it.item.background != Color.Unspecified }.item.background,
        )
    }

    @Test
    fun `unpaired equals are preserved as plain text`() {
        val source = "a = b == c"
        val text = renderInline(source, SpanStyle(background = Color.Yellow))
        assertEquals(source, text.text)
    }

    @Test
    fun `math source and ordinary tildes are preserved`() {
        val source = "Pay ${'$'}100 and ${'$'}50 with ~tilde"
        assertEquals(source, renderInline(source, SpanStyle(background = Color.Yellow)).text)
    }

    @Test
    fun `email autolink drops angle brackets`() {
        val text = renderInline("Mail <user@example.com> now", SpanStyle())
        assertEquals("Mail user@example.com now", text.text)
    }

    @Test
    fun `image alt and reference destination resolve`() {
        val handler = com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl()
        handler.store("logo", "https://example.com/logo.png")
        val source = "![Logo][logo]"
        val tree = MarkdownParser(GFMFlavourDescriptor(), cancellationToken = CancellationToken.NonCancellable)
            .buildMarkdownTreeFromString(source as CharSequence)
        val image = tree.children.first().children.first { it.type.name == "IMAGE" }
        assertEquals("Logo", image.resolveImageAlt(source))
        assertEquals("https://example.com/logo.png", image.resolveImageLink(source, handler))
    }

    @Test
    fun `GitHub alert marker resolves type`() {
        val source = "> [!WARNING]\n> Be careful"
        val tree = MarkdownParser(GFMFlavourDescriptor(), cancellationToken = CancellationToken.NonCancellable)
            .buildMarkdownTreeFromString(source as CharSequence)
        val alert = tree.children.firstOrNull { it.type.name == "ALERT" }
        assertEquals(MarkdownAlertType.WARNING, alert?.findAlertType(source))
    }

    @Test
    fun `default code highlighter colors tokens without changing source`() {
        val colors = MarkdownCodeHighlightColors()
        val source = "val answer = 42 // note\nval text = \"// not comment\""
        val highlights = DefaultMarkdownCodeHighlighter(colors).highlight(source, "kotlin")
        val styled = buildHighlightedCodeString(source, highlights)
        assertEquals(source, styled.text)
        assertTrue(highlights.any { source.substring(it.start, it.end) == "val" && it.color == colors.keyword })
        assertTrue(highlights.any { source.substring(it.start, it.end) == "42" && it.color == colors.number })
        assertTrue(highlights.any { source.substring(it.start, it.end) == "\"// not comment\"" && it.color == colors.string })
        assertTrue(highlights.any { source.substring(it.start, it.end) == "// note" && it.color == colors.comment })
        assertTrue(styled.spanStyles.isNotEmpty())
    }

    @Test
    fun `highlight ranges are rebased for line chunks`() {
        val highlights = listOf(MarkdownCodeHighlight(3, 8, Color.Red))
        val sliced = highlights.sliceForChunk(5, 10)
        assertEquals(1, sliced.size)
        assertEquals(0, sliced[0].start)
        assertEquals(3, sliced[0].end)
    }

    @Test
    fun `code chunk offsets include line separators`() {
        val source = "one\ntwo\nthree"
        val chunks = splitMarkdownCodeChunks(source, 5)
        assertEquals(listOf("one", "two", "three"), chunks.map { it.text })
        assertEquals(
            listOf(0 to 3, 4 to 7, 8 to 13),
            chunks.map { it.startOffset to it.endOffset },
        )
    }

    private fun renderInline(source: String, highlightStyle: SpanStyle): AnnotatedString {
        val tree = MarkdownParser(GFMFlavourDescriptor(), cancellationToken = CancellationToken.NonCancellable)
            .buildMarkdownTreeFromString(source as CharSequence)
        return buildAnnotatedString {
            buildMarkdownAnnotatedString(source, tree.children, TestAnnotatorSettings(highlightStyle))
        }
    }

    private class TestAnnotatorSettings(
        override val highlightSpanStyle: SpanStyle,
    ) : AnnotatorSettings {
        override val linkTextSpanStyle = TextLinkStyles()
        override val codeSpanStyle = SpanStyle()
        override val annotator = DefaultMarkdownAnnotator(null, null, DefaultMarkdownAnnotatorConfig())
        override val referenceLinkHandler = null
        override val linkInteractionListener = null
    }
}
