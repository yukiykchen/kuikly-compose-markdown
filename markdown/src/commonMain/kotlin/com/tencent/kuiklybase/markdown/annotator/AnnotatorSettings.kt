package com.tencent.kuiklybase.markdown.annotator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.tencent.kuikly.compose.ui.text.LinkAnnotation
import com.tencent.kuikly.compose.ui.text.LinkInteractionListener
import com.tencent.kuikly.compose.ui.text.SpanStyle
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownAnnotator
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownColors
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownEmojiShortcodes
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownTypography
import com.tencent.kuiklybase.markdown.compose.LocalReferenceLinkHandler
import com.tencent.kuiklybase.markdown.model.MarkdownAnnotator
import com.tencent.kuiklybase.markdown.model.MarkdownEmojiShortcodes
import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandler
import com.tencent.kuiklybase.markdown.utils.codeSpanStyle
import com.tencent.kuiklybase.markdown.utils.highlightSpanStyle
import com.tencent.kuikly.compose.ui.text.TextLinkStyles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
interface AnnotatorSettings {
    /** Represents the [TextLinkStyles] applied onto links within this annotated string */
    val linkTextSpanStyle: TextLinkStyles

    /** Represents the [SpanStyle] applied onto code inline blocks within this annotated string */
    val codeSpanStyle: SpanStyle

    /** Represents the [SpanStyle] applied to GFM `==highlight==` spans. */
    val highlightSpanStyle: SpanStyle get() = SpanStyle()

    /** Provides custom interception logic for building this annotated string */
    val annotator: MarkdownAnnotator

    /** Optional GitHub-style shortcode text replacement in ordinary prose. */
    val emojiShortcodes: MarkdownEmojiShortcodes? get() = null

    /** Represents the [ReferenceLinkHandler] used to store and find links when clicks on links occur. */
    val referenceLinkHandler: ReferenceLinkHandler?

    /** Represents the [LinkInteractionListener] used for links in this annotated string */
    val linkInteractionListener: LinkInteractionListener?
}

@Immutable
class DefaultAnnotatorSettings(
    override val linkTextSpanStyle: TextLinkStyles,
    override val codeSpanStyle: SpanStyle,
    override val annotator: MarkdownAnnotator,
    override val referenceLinkHandler: ReferenceLinkHandler? = null,
    override val linkInteractionListener: LinkInteractionListener? = null,
    override val highlightSpanStyle: SpanStyle = SpanStyle(),
    override val emojiShortcodes: MarkdownEmojiShortcodes? = null,
) : AnnotatorSettings

@Composable
fun annotatorSettings(
    linkTextSpanStyle: TextLinkStyles = LocalMarkdownTypography.current.textLink,
    codeSpanStyle: SpanStyle = LocalMarkdownTypography.current.codeSpanStyle,
    annotator: MarkdownAnnotator = LocalMarkdownAnnotator.current,
    referenceLinkHandler: ReferenceLinkHandler? = LocalReferenceLinkHandler.current,
    uriHandler: UriHandler = LocalUriHandler.current,
    scope: CoroutineScope = rememberCoroutineScope(),
    linkInteractionListener: LinkInteractionListener? = null,
    highlightSpanStyle: SpanStyle = LocalMarkdownColors.current.highlightSpanStyle,
    emojiShortcodes: MarkdownEmojiShortcodes? = LocalMarkdownEmojiShortcodes.current,
): AnnotatorSettings = remember(
    linkTextSpanStyle,
    codeSpanStyle,
    highlightSpanStyle,
    annotator,
    referenceLinkHandler,
    uriHandler,
    scope,
    linkInteractionListener,
    emojiShortcodes,
) {
    val defaultLinkInteractionListener = LinkInteractionListener { link ->
        val annotationUrl = (link as? LinkAnnotation.Url)?.url
        if (annotationUrl != null) {
            val foundReference = referenceLinkHandler?.find(annotationUrl)?.takeIf { it.isNotEmpty() } ?: annotationUrl
            try {
                scope.launch { uriHandler.openUri(foundReference) }
            } catch (t: Throwable) {
                println("Could not open the provided url: $foundReference // ${t.message}")
            }
        }
    }
    val effectiveLinkInteractionListener = linkInteractionListener ?: defaultLinkInteractionListener
    DefaultAnnotatorSettings(
        linkTextSpanStyle = linkTextSpanStyle,
        codeSpanStyle = codeSpanStyle,
        highlightSpanStyle = highlightSpanStyle,
        annotator = annotator,
        referenceLinkHandler = referenceLinkHandler,
        linkInteractionListener = effectiveLinkInteractionListener,
        emojiShortcodes = emojiShortcodes,
    )
}
