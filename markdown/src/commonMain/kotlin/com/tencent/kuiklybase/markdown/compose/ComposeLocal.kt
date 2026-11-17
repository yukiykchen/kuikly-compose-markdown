package com.tencent.kuiklybase.markdown.compose

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.tencent.kuiklybase.markdown.compose.components.MarkdownComponents
import com.tencent.kuiklybase.markdown.compose.components.markdownComponents
import com.tencent.kuiklybase.markdown.model.BulletHandler
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownAnnotator
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownAnnotatorConfig
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownExtendedSpans
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownCodeHighlighterInstance
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownEmojiShortcodes
import com.tencent.kuiklybase.markdown.model.ImageLinkHandler
import com.tencent.kuiklybase.markdown.model.ImageLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.ImageTransformer
import com.tencent.kuiklybase.markdown.model.MarkdownAnimations
import com.tencent.kuiklybase.markdown.model.MarkdownAnnotator
import com.tencent.kuiklybase.markdown.model.MarkdownColors
import com.tencent.kuiklybase.markdown.model.MarkdownCodeHighlighter
import com.tencent.kuiklybase.markdown.model.MarkdownDimens
import com.tencent.kuiklybase.markdown.model.MarkdownExtendedSpans
import com.tencent.kuiklybase.markdown.model.MarkdownEmojiShortcodes
import com.tencent.kuiklybase.markdown.model.MarkdownRenderCache
import com.tencent.kuiklybase.markdown.model.MarkdownPadding
import com.tencent.kuiklybase.markdown.model.MarkdownTypography
import com.tencent.kuiklybase.markdown.model.NoOpTextProvider
import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandler
import com.tencent.kuiklybase.markdown.model.TextProvider
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownColors
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownTypography
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownAnimation
import com.tencent.kuiklybase.markdown.model.NoOpImageTransformerImpl
import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.markdownPadding
import com.tencent.kuiklybase.markdown.model.markdownDimens

/**
 * The CompositionLocal to provide functionality related to transforming the bullet of an ordered list
 */
val LocalBulletListHandler = staticCompositionLocalOf {
    return@staticCompositionLocalOf BulletHandler { _, _, _, _ -> "● " }
}

/**
 * The CompositionLocal to provide functionality related to transforming the bullet of an ordered list
 */
val LocalOrderedListHandler = staticCompositionLocalOf {
    return@staticCompositionLocalOf BulletHandler { _, _, index, _ -> "${index + 1}. " }
}

/**
 * Local [ReferenceLinkHandler] provider
 */
val LocalReferenceLinkHandler = staticCompositionLocalOf<ReferenceLinkHandler> {
    ReferenceLinkHandlerImpl()
}

/**
 * Local [MarkdownColors] provider
 */
val LocalMarkdownColors = compositionLocalOf<MarkdownColors> {
    DefaultMarkdownColors()
}

/**
 * Local [MarkdownTypography] provider
 */
val LocalMarkdownTypography = compositionLocalOf<MarkdownTypography> {
    DefaultMarkdownTypography()
}

/**
 * Local [MarkdownPadding] provider
 */
val LocalMarkdownPadding = staticCompositionLocalOf<MarkdownPadding> {
    markdownPadding()
}

/**
 * Local [MarkdownDimens] provider
 */
val LocalMarkdownDimens = compositionLocalOf<MarkdownDimens> {
    markdownDimens()
}

/**
 * Local [ImageTransformer] provider
 */
val LocalImageTransformer = staticCompositionLocalOf<ImageTransformer> {
    NoOpImageTransformerImpl()
}

val LocalImageLinkHandler = staticCompositionLocalOf<ImageLinkHandler> { ImageLinkHandlerImpl() }

val LocalTextProvider = staticCompositionLocalOf<TextProvider> { NoOpTextProvider() }

/**
 * Local [MarkdownAnnotator] provider
 */
val LocalMarkdownAnnotator = compositionLocalOf<MarkdownAnnotator> {
    return@compositionLocalOf DefaultMarkdownAnnotator(null, null, DefaultMarkdownAnnotatorConfig())
}

/**
 * Local [MarkdownExtendedSpans] provider
 */
val LocalMarkdownExtendedSpans = compositionLocalOf<MarkdownExtendedSpans> {
    return@compositionLocalOf DefaultMarkdownExtendedSpans(null)
}

/**
 * Local [MarkdownComponents] provider
 */
val LocalMarkdownComponents = compositionLocalOf<MarkdownComponents> {
    return@compositionLocalOf markdownComponents()
}

/** Replaces the tokenizer used by the default code-block renderer. */
val LocalMarkdownCodeHighlighter = staticCompositionLocalOf<MarkdownCodeHighlighter> {
    DefaultMarkdownCodeHighlighterInstance
}

/**
 * Optional GitHub-style `:shortcode:` replacement for ordinary prose.
 * Set to null to preserve shortcodes as literal text.
 */
val LocalMarkdownEmojiShortcodes = staticCompositionLocalOf<MarkdownEmojiShortcodes?> {
    DefaultMarkdownEmojiShortcodes
}

/**
 * Local [MarkdownAnimations] provider
 */
val LocalMarkdownAnimations = compositionLocalOf<MarkdownAnimations> {
    DefaultMarkdownAnimation { this }
}

/** Cache for rendered inline text that survives LazyColumn item disposal. */
val LocalMarkdownRenderCache = staticCompositionLocalOf<MarkdownRenderCache?> { null }

/** Generation for inline text affected by a late Markdown reference definition. */
val LocalMarkdownBlockLinkGeneration = staticCompositionLocalOf { 0 }
