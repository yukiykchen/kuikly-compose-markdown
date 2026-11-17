package com.tencent.kuiklybase.markdown.compose.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.foundation.BorderStroke
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.shadow
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.Shape
import com.tencent.kuikly.compose.ui.text.AnnotatedString
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.LayoutDirection
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownColors
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownCodeHighlighter
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownDimens
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownPadding
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownTypography
import com.tencent.kuiklybase.markdown.compose.elements.material.MarkdownBasicText
import com.tencent.kuiklybase.markdown.model.buildHighlightedCodeString
import com.tencent.kuiklybase.markdown.model.sliceForChunk
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode

@Composable
fun MarkdownCode(
    code: String,
    language: String?,
    style: TextStyle = LocalMarkdownTypography.current.code,
    chunkCharacters: Int = 4096,
    highlighterEnabled: Boolean = true,
    showLanguageHeader: Boolean = true,
) {
    val backgroundCodeColor = LocalMarkdownColors.current.codeBackground
    val codeBackgroundCornerSize = LocalMarkdownDimens.current.codeBackgroundCornerSize
    val codeBlockPadding = LocalMarkdownPadding.current.codeBlock
    val highlighter = LocalMarkdownCodeHighlighter.current
    val highlighted = if (highlighterEnabled) {
        remember(code, language, highlighter, style) { highlighter.highlight(code, language) }
    } else {
        emptyList()
    }
    MarkdownCodeBackground(
        color = backgroundCodeColor,
        shape = RoundedCornerShape(codeBackgroundCornerSize),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        language = language,
        showLanguageHeader = showLanguageHeader,
    ) {
        Column(
            modifier = Modifier.padding(
                    start = codeBlockPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = codeBlockPadding.calculateRightPadding(LayoutDirection.Ltr),
                    top = codeBlockPadding.calculateTopPadding(),
                    bottom = codeBlockPadding.calculateBottomPadding(),
                ),
        ) {
            splitMarkdownCodeChunks(code, chunkCharacters).forEach { chunk ->
                key(chunk.startOffset) {
                    MarkdownBasicText(
                        text = remember(chunk.text, highlighted, style) {
                            buildHighlightedCodeString(
                                chunk.text,
                                highlighted.sliceForChunk(chunk.startOffset, chunk.endOffset),
                            )
                        },
                        color = LocalMarkdownColors.current.codeText,
                        style = style,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

internal data class MarkdownCodeChunk(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
)

/** Split only between complete lines: joining the chunks with LF reproduces the input exactly. */
internal fun splitMarkdownCodeLines(code: String, chunkCharacters: Int): List<String> {
    require(chunkCharacters > 0)
    if (code.length <= chunkCharacters) return listOf(code)
    val chunks = mutableListOf<String>()
    var current = StringBuilder()
    var lineCount = 0
    code.splitToSequence('\n').forEach { line ->
        val separator = if (lineCount == 0) 0 else 1
        if (lineCount > 0 && current.length.toLong() + separator + line.length > chunkCharacters) {
            chunks.add(current.toString())
            current = StringBuilder()
            lineCount = 0
        }
        if (lineCount > 0) current.append('\n')
        current.append(line)
        lineCount++
    }
    chunks.add(current.toString())
    return chunks
}

/** Preserves absolute offsets so token styles can be sliced per complete-line chunk. */
internal fun splitMarkdownCodeChunks(code: String, chunkCharacters: Int): List<MarkdownCodeChunk> {
    require(chunkCharacters > 0)
    if (code.length <= chunkCharacters) return listOf(MarkdownCodeChunk(code, 0, code.length))
    val chunks = mutableListOf<MarkdownCodeChunk>()
    val lines = splitMarkdownCodeLines(code, chunkCharacters)
    var start = 0
    lines.forEachIndexed { index, text ->
        chunks += MarkdownCodeChunk(text, start, start + text.length)
        if (index < lines.lastIndex) start += text.length + 1 // one LF separator between chunks
    }
    return chunks
}

@Composable
fun MarkdownCodeFence(
    content: String,
    node: ASTNode,
    style: TextStyle = LocalMarkdownTypography.current.code,
    block: @Composable (String, String?, TextStyle) -> Unit = { code, language, style ->
        MarkdownCode(
            code = code,
            language = language,
            style = style
        )
    },
) {
    // CODE_FENCE_START, FENCE_LANG, EOL, {content // CODE_FENCE_CONTENT // x-times}, CODE_FENCE_END
    // CODE_FENCE_START, EOL, {content // CODE_FENCE_CONTENT // x-times}, EOL
    // CODE_FENCE_START, EOL, {content // CODE_FENCE_CONTENT // x-times}
    // CODE_FENCE_START, FENCE_LANG, EOL, {content // CODE_FENCE_CONTENT // x-times}

    val language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(content)?.toString()
    if (node.children.size >= 3) {
        val start = node.children[2].startOffset
        val minCodeFenceCount = if (language != null && node.children.size > 3) 3 else 2
        val end = node.children[(node.children.size - 2).coerceAtLeast(minCodeFenceCount)].endOffset
        block(content.subSequence(start, end).toString().replaceIndent(), language, style)
    } else {
        // invalid code block, skipping
    }
}

@Composable
fun MarkdownCodeBlock(
    content: String,
    node: ASTNode,
    style: TextStyle = LocalMarkdownTypography.current.code,
    block: @Composable (String, String?, TextStyle) -> Unit = { code, language, style ->
        MarkdownCode(code = code, language = language, style = style)
    },
) {
    val start = node.children[0].startOffset
    val end = node.children[node.children.size - 1].endOffset
    val language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(content)?.toString()
    block(content.subSequence(start, end).toString().replaceIndent(), language, style)
}

@Composable
fun MarkdownCodeBackground(
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0F),
    border: BorderStroke? = null,
    elevation: Dp = 0.dp,
    language: String? = null,
    showLanguageHeader: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(elevation)
            .then(if (border != null) Modifier.border(border) else Modifier)
            .background(color = color)
            .clip(shape)
    ) {
        Column {
            if (showLanguageHeader && !language.isNullOrBlank()) {
                MarkdownBasicText(
                    text = language.uppercase(),
                    color = LocalMarkdownColors.current.codeText,
                    style = LocalMarkdownTypography.current.code,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp),
                )
                MarkdownDivider(
                    color = LocalMarkdownColors.current.dividerColor,
                    thickness = 0.5.dp,
                )
            }
            content()
        }
    }
}
