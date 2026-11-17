package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.tencent.kuiklybase.markdown.compose.extendedspans.ExtendedSpans

interface MarkdownExtendedSpans {
    val extendedSpans: (@Composable () -> ExtendedSpans)?
}

/**
 * Kuikly 2.30+ maps `SpanStyle.background` to the native RichText span background.
 * Custom painters that strip the background are incompatible with that path.
 */
@Immutable
class DefaultMarkdownExtendedSpans(
    override val extendedSpans: (@Composable () -> ExtendedSpans)?
) : MarkdownExtendedSpans

@Composable
@Deprecated(
    "Background spans are rendered natively by Kuikly 2.30+; pass no custom painters.",
    ReplaceWith("markdownExtendedSpans()"),
)
fun markdownExtendedSpans(
    extendedSpans: (@Composable () -> ExtendedSpans)? = null
): MarkdownExtendedSpans = DefaultMarkdownExtendedSpans(extendedSpans)

@Composable
fun markdownExtendedSpans(): MarkdownExtendedSpans = DefaultMarkdownExtendedSpans(null)
