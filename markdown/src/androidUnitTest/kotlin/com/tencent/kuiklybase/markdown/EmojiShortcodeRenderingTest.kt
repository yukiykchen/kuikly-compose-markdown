package com.tencent.kuiklybase.markdown

import com.tencent.kuikly.compose.ui.text.AnnotatedString
import com.tencent.kuikly.compose.ui.text.SpanStyle
import com.tencent.kuikly.compose.ui.text.TextLinkStyles
import com.tencent.kuikly.compose.ui.text.buildAnnotatedString
import com.tencent.kuiklybase.markdown.annotator.AnnotatorSettings
import com.tencent.kuiklybase.markdown.annotator.buildMarkdownAnnotatedString
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownAnnotator
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownAnnotatorConfig
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownEmojiShortcodes
import com.tencent.kuiklybase.markdown.model.MarkdownEmojiShortcodes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiShortcodeRenderingTest {
    @Test
    fun `default shortcodes cover the full syntax demo categories`() {
        val source = """
            表情：:smile: :laughing: :joy: :heart_eyes: :thinking: :sunglasses:
            手势：:thumbsup: :clap: :wave: :ok_hand: :pray: :muscle:
            符号：:heart: :star: :fire: :100: :sparkles: :zap:
            状态：:white_check_mark: :x: :warning: :question: :exclamation:
            物品：:rocket: :trophy: :tada: :gift: :computer: :phone:
            自然：:sunny: :rainbow: :ocean: :snowflake: :seedling:
            动物：:dog: :cat: :monkey: :bug:
            食物：:coffee: :pizza: :beer: :cake: :apple:
        """.trimIndent()
        val text = renderInline(source)
        listOf(
            "😄", "😆", "😂", "😍", "🤔", "😎",
            "👍", "👏", "👋", "👌", "🙏", "💪",
            "❤️", "⭐", "🔥", "💯", "✨", "⚡",
            "✅", "❌", "⚠️", "❓", "❗",
            "🚀", "🏆", "🎉", "🎁", "💻", "☎️",
            "☀️", "🌈", "🌊", "❄️", "🌱",
            "🐶", "🐱", "🐒", "🐛",
            "☕", "🍕", "🍺", "🍰", "🍎",
        ).forEach { emoji -> assertTrue(emoji in text.text, "$emoji missing from ${text.text}") }
        assertTrue(":smile:" !in text.text)
        assertTrue(":rocket:" !in text.text)
    }

    @Test
    fun `code spans and link URLs preserve literal shortcodes`() {
        val source = "code `:smile:` link [text](https://a.test/:smile:) :rocket:"
        val rendered = renderInline(source)
        assertEquals("code  :smile:  link text 🚀", rendered.text)
    }

    @Test
    fun `unknown and incomplete shortcodes are preserved during streaming`() {
        val source = "unknown :unknown: half :smi ending: :rocket"
        assertEquals("unknown :unknown: half :smi ending: :rocket", renderInline(source).text)
    }

    @Test
    fun `escaped shortcode is plain text`() {
        val source = "escaped \\:smile: ending:"
        assertEquals("escaped :smile: ending:", renderInline(source).text)
    }

    @Test
    fun `shortcodes can be disabled or mapped by host`() {
        assertEquals(":rocket:", renderInline(":rocket:", null).text)
        assertEquals("ROCKET", renderInline(":rocket:", MarkdownEmojiShortcodes { "ROCKET" }).text)
    }

    private fun renderInline(
        source: String,
        shortcodes: MarkdownEmojiShortcodes? = DefaultMarkdownEmojiShortcodes,
    ): AnnotatedString {
        val tree = MarkdownParser(GFMFlavourDescriptor(), cancellationToken = CancellationToken.NonCancellable)
            .buildMarkdownTreeFromString(source as CharSequence)
        return buildAnnotatedString {
            buildMarkdownAnnotatedString(source, tree.children, TestAnnotatorSettings(shortcodes))
        }
    }

    private class TestAnnotatorSettings(
        override val emojiShortcodes: MarkdownEmojiShortcodes?,
    ) : AnnotatorSettings {
        override val linkTextSpanStyle = TextLinkStyles()
        override val codeSpanStyle = SpanStyle()
        override val highlightSpanStyle = SpanStyle()
        override val annotator = DefaultMarkdownAnnotator(null, null, DefaultMarkdownAnnotatorConfig())
        override val referenceLinkHandler = null
        override val linkInteractionListener = null
    }
}
