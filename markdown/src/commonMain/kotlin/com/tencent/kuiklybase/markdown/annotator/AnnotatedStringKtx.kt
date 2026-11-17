package com.tencent.kuiklybase.markdown.annotator

import com.tencent.kuikly.compose.ui.text.font.FontStyle
import com.tencent.kuikly.compose.ui.text.style.TextDecoration
import com.tencent.kuikly.compose.ui.text.AnnotatedString
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.SpanStyle
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.foundation.text.appendInlineContent
import com.tencent.kuikly.compose.ui.text.LinkAnnotation
import com.tencent.kuikly.compose.ui.text.LinkInteractionListener
import com.tencent.kuikly.compose.ui.text.buildAnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownRenderCache
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownBlockLinkGeneration
import com.tencent.kuiklybase.markdown.model.MarkdownAnnotator
import com.tencent.kuiklybase.markdown.model.MarkdownEmojiShortcodes
import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandler
import com.tencent.kuiklybase.markdown.model.markdownAnnotator
import com.tencent.kuiklybase.markdown.utils.MARKDOWN_TAG_IMAGE_URL
import com.tencent.kuiklybase.markdown.utils.findChildOfTypeRecursive
import com.tencent.kuiklybase.markdown.utils.getUnescapedTextInNode
import com.tencent.kuiklybase.markdown.utils.innerList
import com.tencent.kuiklybase.markdown.utils.mapAutoLinkToType
import com.tencent.kuiklybase.markdown.utils.resolveImageLink
import com.tencent.kuikly.compose.ui.text.TextLinkStyles
import com.tencent.kuikly.compose.ui.text.withLink
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import com.tencent.kuiklybase.markdown.utils.normalizeMarkdownDestination
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import com.tencent.kuiklybase.markdown.compat.parseMarkdown

/**
 * Extension function to build an `AnnotatedString` from a Markdown string.
 * This function will parse the Markdown content and apply the given styles to the text.
 *
 * It only supports TEXT and PARAGRAPH nodes.
 *
 * @param style The base text style to apply.
 * @param linkTextSpanStyle The style to apply to link text.
 * @param codeSpanStyle The style to apply to code spans.
 * @param flavour The Markdown flavour descriptor to use (default is GFM).
 * @param annotator An optional annotator for additional processing.
 * @return The constructed `AnnotatedString`.
 */
@Deprecated(
    message = "This function is deprecated. Use the new `annotatorSettings` function to create a settings object.",
    replaceWith = ReplaceWith("buildMarkdownAnnotatedString(style, annotatorSettings, flavour)")
)
@Composable
fun String.buildMarkdownAnnotatedString(
    style: TextStyle,
    linkTextSpanStyle: SpanStyle = style.toSpanStyle(),
    codeSpanStyle: SpanStyle = style.toSpanStyle(),
    flavour: MarkdownFlavourDescriptor = GFMFlavourDescriptor(),
    annotator: MarkdownAnnotator = markdownAnnotator(),
    referenceLinkHandler: ReferenceLinkHandler? = null,
    linkInteractionListener: LinkInteractionListener? = null,
) = buildMarkdownAnnotatedString(
    style = style,
    annotatorSettings = DefaultAnnotatorSettings(
        linkTextSpanStyle = TextLinkStyles(style = linkTextSpanStyle),
        codeSpanStyle = codeSpanStyle,
        annotator = annotator,
        referenceLinkHandler = referenceLinkHandler,
        linkInteractionListener = linkInteractionListener
    ),
    flavour = flavour
)

/**
 * Extension function to build an `AnnotatedString` from a Markdown string.
 * This function will parse the Markdown content and apply the given styles to the text.
 *
 * It only supports TEXT and PARAGRAPH nodes.
 *
 * @param style The base text style to apply.
 * @param flavour The Markdown flavour descriptor to use (default is GFM).
 * @param annotatorSettings Settings object to adjust different behavior of this annotated string.
 * @return The constructed `AnnotatedString`.
 */
@Composable
fun String.buildMarkdownAnnotatedString(
    style: TextStyle,
    annotatorSettings: AnnotatorSettings,
    flavour: MarkdownFlavourDescriptor = GFMFlavourDescriptor(),
): AnnotatedString {
    val content = this
    val parsedTree = parseMarkdown(flavour, CancellationToken.NonCancellable, content)
    val textNode = parsedTree.children.firstOrNull { node ->
        node.type == MarkdownTokenTypes.TEXT || node.type == MarkdownElementTypes.PARAGRAPH
    }
    if (textNode == null) return buildAnnotatedString { }
    return content.buildMarkdownAnnotatedString(
        textNode = textNode,
        style = style,
        annotatorSettings = annotatorSettings
    )
}

/**
 * Extension function to build an `AnnotatedString` from a Markdown string.
 * This function will parse the Markdown content and apply the given styles to the text.
 *
 * It only supports TEXT and PARAGRAPH nodes.
 *
 * @param textNode The AST node representing the text.
 * @param style The base text style to apply.
 * @param annotatorSettings Settings object to adjust different behavior of this annotated string.
 * @return The constructed `AnnotatedString`.
 */
@Composable
fun String.buildMarkdownAnnotatedString(
    textNode: ASTNode,
    style: TextStyle,
    annotatorSettings: AnnotatorSettings,
): AnnotatedString = remember(this, textNode, style, annotatorSettings) {
    buildAnnotatedString {
        pushStyle(style.toSpanStyle())
        buildMarkdownAnnotatedString(this@buildMarkdownAnnotatedString, textNode, annotatorSettings)
        pop()
    }
}

@Composable
internal fun cachedMarkdownAnnotatedString(
    content: String,
    textNode: ASTNode,
    style: TextStyle,
    annotatorSettings: AnnotatorSettings,
): AnnotatedString {
    val source = content.substring(
        textNode.startOffset.coerceAtLeast(0),
        textNode.endOffset.coerceAtMost(content.length),
    )
    val build = {
        buildAnnotatedString {
            pushStyle(style.toSpanStyle())
            buildMarkdownAnnotatedString(content, textNode, annotatorSettings)
            pop()
        }
    }
    val cache = LocalMarkdownRenderCache.current
    val cacheRevision = cache?.revision
    val linkGeneration = LocalMarkdownBlockLinkGeneration.current
    return if (cache != null) {
        remember(source, textNode, style, annotatorSettings, cache, cacheRevision, linkGeneration) {
            cache.getOrPut(content, textNode, style, annotatorSettings, build, generation = linkGeneration)
        }
    } else {
        remember(source, textNode, style, annotatorSettings, linkGeneration) {
            build()
        }
    }
}

/**
 * Appends a Markdown link to the `AnnotatedString.Builder`.
 *
 * @param content The content string.
 * @param node The AST node representing the link.
 * @param annotatorSettings Settings object to adjust different behavior of this annotated string.
 */
internal fun AnnotatedString.Builder.appendMarkdownLink(
    content: String,
    node: ASTNode,
    annotatorSettings: AnnotatorSettings,
) {
    val linkText = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.children?.innerList()
    if (linkText == null) {
        append(node.getUnescapedTextInNode(content))
        return
    }
    val text = linkText.firstOrNull()?.getUnescapedTextInNode(content)
    val destination = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
        ?.let { normalizeMarkdownDestination(it.getUnescapedTextInNode(content)) }
    val linkLabel = node.findChildOfType(MarkdownElementTypes.LINK_LABEL)?.getUnescapedTextInNode(content)
    val annotation = destination ?: linkLabel

    if (annotation != null) {
        if (text != null) annotatorSettings.referenceLinkHandler?.store(text, annotation)
        withLink(
            LinkAnnotation.Url(
                annotation,
                annotatorSettings.linkTextSpanStyle,
                annotatorSettings.linkInteractionListener
            )
        ) {
            buildMarkdownAnnotatedString(
                content,
                linkText.mapAutoLinkToType(),
                annotatorSettings.withoutEmojiShortcodes(),
            )
        }
    } else {
        buildMarkdownAnnotatedString(content, linkText, annotatorSettings.withoutEmojiShortcodes())
    }
}

/**
 * Appends a Markdown reference to the `AnnotatedString.Builder`.
 *
 * @param content The content string.
 * @param node The AST node representing the link.
 * @param annotatorSettings Settings object to adjust different behavior of this annotated string.
 */
internal fun AnnotatedString.Builder.appendMarkdownReference(
    content: String,
    node: ASTNode,
    annotatorSettings: AnnotatorSettings,
) {
    val full = node.type == MarkdownElementTypes.FULL_REFERENCE_LINK
    val labelNode = node.findChildOfType(MarkdownElementTypes.LINK_LABEL)
    val linkText = if (full) {
        node.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.children?.innerList()
    } else {
        labelNode?.children?.innerList()
    }

    if (linkText == null || labelNode == null) {
        append(node.getUnescapedTextInNode(content))
        return
    }

    val label = labelNode.getUnescapedTextInNode(content)
    val url = annotatorSettings.referenceLinkHandler?.find(label)?.takeIf { it.isNotEmpty() }

    if (url != null) {
        withLink(
            LinkAnnotation.Url(
                url,
                annotatorSettings.linkTextSpanStyle,
                annotatorSettings.linkInteractionListener
            )
        ) {
            buildMarkdownAnnotatedString(
                content,
                linkText.mapAutoLinkToType(),
                annotatorSettings.withoutEmojiShortcodes(),
            )
        }
    } else {
        // The current Kuikly parser shape keeps brackets in labelNode; upstream's
        // linkText + labelNode split would duplicate short references like [ref].
        append(node.getUnescapedTextInNode(content))
    }
}

/** Appends an email autolink without its surrounding angle brackets. */
internal fun AnnotatedString.Builder.appendEmailAutoLink(
    content: String,
    node: ASTNode,
    annotatorSettings: AnnotatorSettings,
) {
    val email = node.getUnescapedTextInNode(content)
    val destination = "mailto:$email"
    annotatorSettings.referenceLinkHandler?.store(email, destination)
    withLink(
        LinkAnnotation.Url(
            destination,
            annotatorSettings.linkTextSpanStyle,
            linkInteractionListener = annotatorSettings.linkInteractionListener
        )
    ) {
        append(email)
    }
}

/**
 * Appends an auto-detected link to the `AnnotatedString.Builder`.
 *
 * @param content The content string.
 * @param node The AST node representing the auto link.
 * @param annotatorSettings The style to apply to the link text.
 */
internal fun AnnotatedString.Builder.appendAutoLink(
    content: String,
    node: ASTNode,
    annotatorSettings: AnnotatorSettings,
) {
    val targetNode = node.children.firstOrNull {
        it.type.name == MarkdownElementTypes.AUTOLINK.name
    } ?: node
    val destination = targetNode.getUnescapedTextInNode(content)

    annotatorSettings.referenceLinkHandler?.store(destination, destination)
    withLink(
        LinkAnnotation.Url(
            destination,
            annotatorSettings.linkTextSpanStyle,
            linkInteractionListener = annotatorSettings.linkInteractionListener
        )
    ) {
        append(destination)
    }
}

/**
 * Builds an [AnnotatedString] with the contents of the given Markdown [ASTNode] node.
 *
 * This method automatically constructs the string with child components like:
 * - Paragraph
 * - Image
 * - Strong
 * - ...
 */
fun AnnotatedString.Builder.buildMarkdownAnnotatedString(
    content: String,
    node: ASTNode,
    annotatorSettings: AnnotatorSettings,
) = buildMarkdownAnnotatedString(
    content = content,
    children = node.children,
    annotatorSettings = annotatorSettings,
)

/**
 * Builds an [AnnotatedString] with the contents of the given Markdown [ASTNode] node.
 *
 * This method automatically constructs the string with child components like:
 * - Paragraph
 * - Image
 * - Strong
 * - ...
 */
fun AnnotatedString.Builder.buildMarkdownAnnotatedString(
    content: String,
    children: List<ASTNode>,
    annotatorSettings: AnnotatorSettings,
) {
    val annotate = annotatorSettings.annotator.annotate
    val eolAsNewLine = annotatorSettings.annotator.config.eolAsNewLine
    val emojiShortcodes = annotatorSettings.emojiShortcodes
    var pendingColon: ASTNode? = null
    var pendingAlias: ASTNode? = null
    var skipIfNext: Any? = null
    children.forEachIndexed { index, child ->
        if (skipIfNext == null || skipIfNext != child.type) {
            if (pendingColon != null && pendingAlias == null && !isEmojiAliasNode(child)) {
                append(':')
                pendingColon = null
            } else if (pendingAlias != null && !isEmojiClosingColon(child)) {
                append(':')
                appendInlineText(content, pendingAlias!!, emojiShortcodes, localOnly = true)
                pendingColon = null
                pendingAlias = null
            }
            if (annotate == null || !annotate(content, child, annotatorSettings)) {
                val parentType = child.parent?.type

                when (child.type) {
                    // Element types
                    MarkdownElementTypes.PARAGRAPH -> buildMarkdownAnnotatedString(
                        content = content,
                        node = child,
                        annotatorSettings = annotatorSettings
                    )

                    MarkdownElementTypes.IMAGE -> child.resolveImageLink(
                        content,
                        annotatorSettings.referenceLinkHandler,
                    )?.let {
                        appendInlineContent(MARKDOWN_TAG_IMAGE_URL, it)
                    }

                    MarkdownElementTypes.EMPH -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        buildMarkdownAnnotatedString(content, child, annotatorSettings)
                        pop()
                    }

                    MarkdownElementTypes.STRONG -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        buildMarkdownAnnotatedString(content, child, annotatorSettings)
                        pop()
                    }

                    GFMElementTypes.STRIKETHROUGH -> {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        buildMarkdownAnnotatedString(content, child, annotatorSettings)
                        pop()
                    }

                    MarkdownElementTypes.CODE_SPAN -> {
                        val codeSettings = annotatorSettings.withoutEmojiShortcodes()
                        pushStyle(annotatorSettings.codeSpanStyle)
                        append(' ')
                        buildMarkdownAnnotatedString(content, child.children.innerList(), codeSettings)
                        append(' ')
                        pop()
                    }

                    MarkdownElementTypes.AUTOLINK -> appendAutoLink(content, child, annotatorSettings)
                    MarkdownElementTypes.INLINE_LINK -> appendMarkdownLink(content, child, annotatorSettings)
                    MarkdownElementTypes.SHORT_REFERENCE_LINK -> appendMarkdownReference(
                        content,
                        child,
                        annotatorSettings
                    )

                    MarkdownElementTypes.FULL_REFERENCE_LINK -> appendMarkdownReference(
                        content,
                        child,
                        annotatorSettings
                    )

                    // Token Types
                    MarkdownTokenTypes.TEXT -> if (pendingColon != null && pendingAlias == null) {
                        pendingAlias = child
                    } else {
                        appendInlineText(
                            content = content,
                            node = child,
                            emojiShortcodes = emojiShortcodes,
                        )
                    }
                    GFMTokenTypes.GFM_AUTOLINK -> if (parentType == MarkdownElementTypes.LINK_TEXT) {
                        appendInlineText(
                            content = content,
                            node = child,
                            emojiShortcodes = emojiShortcodes,
                        )
                    } else appendAutoLink(content, child, annotatorSettings)

                    MarkdownTokenTypes.SINGLE_QUOTE -> append('\'')
                    MarkdownTokenTypes.DOUBLE_QUOTE -> append('\"')
                    MarkdownTokenTypes.LPAREN -> append('(')
                    MarkdownTokenTypes.RPAREN -> append(')')
                    MarkdownTokenTypes.LBRACKET -> append('[')
                    MarkdownTokenTypes.RBRACKET -> append(']')
                    MarkdownTokenTypes.LT -> if (children.getOrNull(index + 1)?.type?.name != "EMAIL_AUTOLINK") {
                        append('<')
                    }

                    MarkdownTokenTypes.GT -> if (children.getOrNull(index - 1)?.type?.name != "EMAIL_AUTOLINK") {
                        append('>')
                    }
                    MarkdownTokenTypes.COLON -> handleColonToken(
                        content = content,
                        children = children,
                        index = index,
                        node = child,
                        parentType = parentType,
                        emojiShortcodes = emojiShortcodes,
                        pendingColon = { pendingColon },
                        pendingAlias = { pendingAlias },
                        setPendingAlias = { pendingAlias = it },
                        setPendingColon = { pendingColon = it },
                    )
                    MarkdownTokenTypes.EXCLAMATION_MARK -> append('!')
                    MarkdownTokenTypes.BACKTICK -> append('`')
                    MarkdownTokenTypes.HARD_LINE_BREAK -> {
                        append('\n')
                        skipIfNext = MarkdownTokenTypes.EOL
                    }

                    MarkdownTokenTypes.EMPH -> if (parentType != MarkdownElementTypes.EMPH && parentType != MarkdownElementTypes.STRONG) {
                        append(child.getTextInNode(content))
                    }

                    MarkdownTokenTypes.EOL -> if (eolAsNewLine) append('\n') else append(' ')
                    MarkdownTokenTypes.WHITE_SPACE -> if (length > 0) append(' ')
                    MarkdownTokenTypes.BLOCK_QUOTE -> {
                        skipIfNext = MarkdownTokenTypes.WHITE_SPACE
                    }

                    else -> when (child.type.name) {
                        ":" -> handleColonToken(
                            content = content,
                            children = children,
                            index = index,
                            node = child,
                            parentType = parentType,
                            emojiShortcodes = emojiShortcodes,
                            pendingColon = { pendingColon },
                            pendingAlias = { pendingAlias },
                            setPendingAlias = { pendingAlias = it },
                            setPendingColon = { pendingColon = it },
                        )
                        "HIGHLIGHT" -> appendHighlight(content, child, annotatorSettings)
                        "EQUALS", "=" -> append('=')
                        "DOLLAR", "$" -> append('$')
                        "INLINE_MATH", "BLOCK_MATH" -> append(child.getTextInNode(content))
                        "EMAIL_AUTOLINK" -> if (parentType == MarkdownElementTypes.LINK_TEXT) {
                            append(child.getUnescapedTextInNode(content))
                        } else appendEmailAutoLink(content, child, annotatorSettings)
                        "~" -> if (parentType != GFMElementTypes.STRIKETHROUGH) {
                            append(child.getTextInNode(content))
                        }
                    }
                }
            }
        } else {
            skipIfNext = null
        }
    }
    if (pendingColon != null) append(':')
    if (pendingAlias != null) {
        appendInlineText(content, pendingAlias!!, emojiShortcodes, localOnly = true)
    }
}

private fun isEmojiAliasNode(node: ASTNode): Boolean =
    isEmojiAliasType(node.type.name) && node.startOffset < node.endOffset

private fun isEmojiAliasType(type: String): Boolean =
    type == MarkdownTokenTypes.TEXT.name || type == "TEXT"

private fun isEmojiClosingColon(node: ASTNode): Boolean =
    node.type.name == MarkdownTokenTypes.COLON.name || node.type.name == ":"

private fun AnnotatedString.Builder.appendInlineText(
    content: String,
    node: ASTNode,
    emojiShortcodes: MarkdownEmojiShortcodes?,
    localOnly: Boolean = false,
) {
    append(childTextWithEmoji(content, node, emojiShortcodes, localOnly))
}

private fun AnnotatorSettings.withoutEmojiShortcodes(): AnnotatorSettings =
    if (emojiShortcodes == null) {
        this
    } else {
        object : AnnotatorSettings by this {
            override val emojiShortcodes: MarkdownEmojiShortcodes? = null
        }
    }

/**
 * Colon-delimited aliases are assembled from parser token boundaries. This
 * keeps literal shortcodes inside code spans and link destinations intact and
 * leaves incomplete aliases unchanged while a stream is still being appended.
 */
private fun AnnotatedString.Builder.handleColonToken(
    content: String,
    children: List<ASTNode>,
    index: Int,
    node: ASTNode,
    parentType: org.intellij.markdown.IElementType?,
    emojiShortcodes: MarkdownEmojiShortcodes?,
    pendingColon: () -> ASTNode?,
    pendingAlias: () -> ASTNode?,
    setPendingAlias: (ASTNode?) -> Unit,
    setPendingColon: (ASTNode?) -> Unit,
) {
    val protectedContext = parentType == MarkdownElementTypes.CODE_SPAN ||
        parentType == MarkdownElementTypes.LINK_TEXT ||
        parentType == MarkdownElementTypes.LINK_DESTINATION ||
        parentType == MarkdownElementTypes.LINK_LABEL ||
        parentType == MarkdownElementTypes.INLINE_LINK ||
        parentType == MarkdownElementTypes.AUTOLINK ||
        parentType == MarkdownElementTypes.FULL_REFERENCE_LINK ||
        parentType == MarkdownElementTypes.SHORT_REFERENCE_LINK
    if (protectedContext || emojiShortcodes == null) {
        append(':')
        return
    }
    val opening = pendingColon()
    val alias = pendingAlias()
    if (opening != null && alias != null) {
        val aliasText = content.substring(alias.startOffset, alias.endOffset)
        val replacement = emojiShortcodes.resolve(aliasText)
        if (replacement.isNullOrEmpty()) {
            append(':')
            appendInlineText(
                content = content,
                node = alias,
                emojiShortcodes = emojiShortcodes,
                localOnly = true,
            )
            append(':')
        } else {
            append(replacement)
        }
        setPendingColon(null)
        setPendingAlias(null)
        return
    }
    if (opening == null) {
        val next = children.getOrNull(index + 1)
        if (next != null && isEmojiAliasNode(next) && isEmojiAliasBoundary(content, node.endOffset)) {
            setPendingColon(node)
        } else {
            append(':')
        }
        return
    }

    append(':')
    setPendingColon(null)
    setPendingAlias(null)
}

private fun isEmojiAliasBoundary(content: String, offset: Int): Boolean =
    offset == 0 || content[offset - 1] != '\\'

private fun childTextWithEmoji(
    content: String,
    node: ASTNode,
    emojiShortcodes: MarkdownEmojiShortcodes?,
    localOnly: Boolean,
): String {
    val source = node.getUnescapedTextInNode(content)
    if (emojiShortcodes == null || source.indexOf(':') < 0 || '\\' in source ||
        node.startOffset + source.length != node.endOffset
    ) {
        return source
    }
    return buildString(source.length) {
        var index = 0
        var pendingOpening = -1
        while (index < source.length) {
            val char = source[index]
            if (char == ':') {
                if (pendingOpening >= 0) {
                    val alias = source.substring(pendingOpening + 1, index)
                    val replacement = emojiShortcodes.resolve(alias)
                    if (replacement.isNullOrEmpty()) {
                        append(':')
                        append(alias)
                        append(':')
                    } else {
                        append(replacement)
                    }
                    pendingOpening = -1
                } else {
                    pendingOpening = index
                }
            } else if (pendingOpening >= 0 &&
                !(char.isLetterOrDigit() || char == '_' || char == '+' || char == '-')
            ) {
                append(':')
                append(source, pendingOpening + 1, index)
                append(char)
                pendingOpening = -1
            } else if (pendingOpening < 0) {
                append(char)
            }
            index++
        }
        if (pendingOpening >= 0) {
            append(':')
            append(source, pendingOpening + 1, source.length)
        }
        val rendered = toString()
        return if (localOnly && rendered.endsWith(":")) rendered.dropLast(1) else rendered
    }
}

private fun AnnotatedString.Builder.appendHighlight(
    content: String,
    child: ASTNode,
    annotatorSettings: AnnotatorSettings,
) {
    pushStyle(annotatorSettings.highlightSpanStyle)
    buildMarkdownAnnotatedString(
        content,
        child.children.drop(2).dropLast(2),
        annotatorSettings
    )
    pop()
}
