package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.Immutable

/**
 * Optional GitHub-style `:shortcode:` text replacement for ordinary prose.
 *
 * Shortcodes are not part of CommonMark or GFM. Rendering only changes the
 * display text; code, URLs, unknown aliases and incomplete streamed tokens
 * remain untouched.
 */
@Immutable
fun interface MarkdownEmojiShortcodes {
    fun resolve(alias: String): String?
}

/** GitHub gemoji aliases used by the full-syntax demo and common AI replies. */
val DefaultMarkdownEmojiShortcodes = MarkdownEmojiShortcodes { alias ->
    defaultMarkdownEmojiShortcodes[alias]
}

/** Empty mapper keeps strict Markdown behavior when emoji extensions are disabled. */
val DisabledMarkdownEmojiShortcodes = MarkdownEmojiShortcodes { null }

private val defaultMarkdownEmojiShortcodes: Map<String, String> = mapOf(
    "smile" to "😄",
    "laughing" to "😆",
    "joy" to "😂",
    "heart_eyes" to "😍",
    "thinking" to "🤔",
    "sunglasses" to "😎",
    "thumbsup" to "👍",
    "+1" to "👍",
    "clap" to "👏",
    "wave" to "👋",
    "ok_hand" to "👌",
    "pray" to "🙏",
    "muscle" to "💪",
    "heart" to "❤️",
    "star" to "⭐",
    "fire" to "🔥",
    "100" to "💯",
    "sparkles" to "✨",
    "zap" to "⚡",
    "white_check_mark" to "✅",
    "x" to "❌",
    "warning" to "⚠️",
    "question" to "❓",
    "exclamation" to "❗",
    "rocket" to "🚀",
    "trophy" to "🏆",
    "tada" to "🎉",
    "gift" to "🎁",
    "computer" to "💻",
    "phone" to "☎️",
    "sunny" to "☀️",
    "rainbow" to "🌈",
    "ocean" to "🌊",
    "snowflake" to "❄️",
    "seedling" to "🌱",
    "dog" to "🐶",
    "cat" to "🐱",
    "monkey" to "🐒",
    "bug" to "🐛",
    "coffee" to "☕",
    "pizza" to "🍕",
    "beer" to "🍺",
    "cake" to "🍰",
    "apple" to "🍎",
    "bulb" to "💡",
)
