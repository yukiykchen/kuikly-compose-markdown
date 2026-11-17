package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.Immutable
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.AnnotatedString
import com.tencent.kuikly.compose.ui.text.SpanStyle
import com.tencent.kuikly.compose.ui.text.buildAnnotatedString
import com.tencent.kuikly.compose.ui.text.font.FontWeight

/** A colored or emphasized range inside a fenced/indented code block. */
@Immutable
data class MarkdownCodeHighlight(
    val start: Int,
    val end: Int,
    val color: Color = Color.Unspecified,
    val bold: Boolean = false,
)

/** Turns source code into styled spans without depending on a UI-specific tokenizer. */
fun interface MarkdownCodeHighlighter {
    fun highlight(code: String, language: String?): List<MarkdownCodeHighlight>
}

/** Syntax colors used by [DefaultMarkdownCodeHighlighter]. */
@Immutable
data class MarkdownCodeHighlightColors(
    val keyword: Color = Color(0xFFCC7832),
    val string: Color = Color(0xFF6A8759),
    val number: Color = Color(0xFF6897BB),
    val comment: Color = Color(0xFF909090),
    val annotation: Color = Color(0xFFBBB529),
    val punctuation: Color = Color(0xFFCC7832),
    val mark: Color = Color.Unspecified,
)

/**
 * A lightweight, dependency-free KMP highlighter for common AI-generated code fences.
 *
 * It follows the same token categories as upstream Highlights (keywords, strings, comments,
 * annotations, literals, punctuation and marks), while keeping the lexer in commonMain so the
 * OHOS/KBA build does not require a third-party klib target.
 */
@Immutable
class DefaultMarkdownCodeHighlighter(
    private val colors: MarkdownCodeHighlightColors = MarkdownCodeHighlightColors(),
) : MarkdownCodeHighlighter {
    override fun highlight(code: String, language: String?): List<MarkdownCodeHighlight> {
        if (code.isEmpty()) return emptyList()
        val keywords = SyntaxKeywords.forLanguage(language)
        val result = mutableListOf<MarkdownCodeHighlight>()
        val protectedRanges = mutableListOf<IntRange>()
        var index = 0

        while (index < code.length) {
            when {
                code.startsWith("/*", index) -> {
                    val end = code.indexOf("*/", index + 2)
                        .let { if (it < 0) code.length else it + 2 }
                    result += MarkdownCodeHighlight(index, end, colors.comment)
                    protectedRanges += index until end
                    index = end
                }

                code.startsWith("//", index) || (language.isPythonLike() && code[index] == '#') -> {
                    val end = code.indexOf('\n', index).let { if (it < 0) code.length else it }
                    result += MarkdownCodeHighlight(index, end, colors.comment)
                    protectedRanges += index until end
                    index = end
                }

                code.startsWith("\"\"\"", index) -> {
                    val end = code.indexOf("\"\"\"", index + 3)
                        .let { if (it < 0) code.length else it + 3 }
                    result += MarkdownCodeHighlight(index, end, colors.string)
                    protectedRanges += index until end
                    index = end
                }

                code[index] == '"' || (code[index] == '\'' && !language.isPythonLike()) -> {
                    val quote = code[index]
                    var end = index + 1
                    while (end < code.length) {
                        if (code[end] == '\\') {
                            end += 2
                            continue
                        }
                        if (code[end] == quote || code[end] == '\n') {
                            if (code[end] == quote) end++
                            break
                        }
                        end++
                    }
                    result += MarkdownCodeHighlight(index, end, colors.string)
                    protectedRanges += index until end
                    index = end
                }

                code[index] == '@' -> {
                    var end = index + 1
                    while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) end++
                    result += MarkdownCodeHighlight(index, end, colors.annotation)
                    protectedRanges += index until end
                    index = end
                }

                code[index].isDigit() -> {
                    var end = index + 1
                    while (end < code.length && (code[end].isLetterOrDigit() || code[end] in "._")) end++
                    if (!isWordStart(code, index)) {
                        index = end
                        continue
                    }
                    result += MarkdownCodeHighlight(index, end, colors.number)
                    protectedRanges += index until end
                    index = end
                }

                code[index].isLetter() || code[index] == '_' -> {
                    var end = index + 1
                    while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) end++
                    val word = code.substring(index, end)
                    if (word in keywords) {
                        result += MarkdownCodeHighlight(index, end, colors.keyword, bold = true)
                        protectedRanges += index until end
                    }
                    index = end
                }

                else -> index++
            }
        }

        code.forEachIndexed { position, char ->
            if (protectedRanges.any { position in it }) return@forEachIndexed
            if (char in ",.:;") {
                result += MarkdownCodeHighlight(position, position + 1, colors.punctuation)
            } else if (char in "(){}=<>-+[]|&") {
                result += MarkdownCodeHighlight(position, position + 1, colors.mark)
            }
        }
        return result.sortedWith(compareBy<MarkdownCodeHighlight> { it.start }.thenBy { it.end })
    }

    private fun isWordStart(code: String, index: Int): Boolean =
        index == 0 || !code[index - 1].isLetterOrDigit() && code[index - 1] != '_'

    private fun String?.isPythonLike(): Boolean =
        this?.lowercase() in setOf("python", "py", "shell", "sh", "bash", "ruby", "rb", "perl")
}

/** The default highlighter can be replaced through [LocalMarkdownCodeHighlighter]. */
val DefaultMarkdownCodeHighlighterInstance = DefaultMarkdownCodeHighlighter()

private object SyntaxKeywords {
    private val common = setOf(
        "as", "break", "case", "catch", "class", "const", "continue", "default", "do", "else",
        "enum", "false", "finally", "for", "fun", "if", "import", "in", "interface", "is",
        "new", "null", "object", "override", "package", "private", "protected", "public",
        "return", "static", "struct", "super", "switch", "this", "throw", "true", "try",
        "val", "var", "void", "when", "while",
    )
    private val kotlin = common + setOf(
        "actual", "abstract", "annotation", "by", "companion", "constructor", "crossinline",
        "data", "dynamic", "expect", "external", "infix", "inline", "internal", "lateinit",
        "noinline", "open", "operator", "out", "reified", "sealed", "suspend", "tailrec",
        "typealias", "vararg", "yield",
    )
    private val java = common + setOf(
        "assert", "boolean", "byte", "char", "double", "extends", "final", "float", "goto",
        "implements", "instanceof", "int", "long", "native", "short", "synchronized", "throws",
        "transient", "volatile",
    )
    private val javascript = common + setOf(
        "async", "await", "debugger", "delete", "export", "extends", "function", "implements",
        "instanceof", "let", "number", "string", "typeof", "undefined", "with", "yield",
    )
    private val python = common + setOf(
        "and", "assert", "async", "await", "def", "del", "elif", "except", "from", "global",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "with", "yield",
    )
    private val all = kotlin + java + javascript + python

    fun forLanguage(language: String?): Set<String> = when (language?.lowercase()) {
        "kotlin", "kt", "kts" -> kotlin
        "java" -> java
        "javascript", "js", "typescript", "ts", "tsx" -> javascript
        "python", "py", "ruby", "rb", "perl", "shell", "sh", "bash" -> python
        else -> all
    }
}

internal fun buildHighlightedCodeString(
    code: String,
    highlights: List<MarkdownCodeHighlight>,
): AnnotatedString = buildAnnotatedString {
    append(code)
    highlights.forEach { highlight ->
        val start = highlight.start.coerceIn(0, code.length)
        val end = highlight.end.coerceIn(start, code.length)
        if (start == end) return@forEach
        addStyle(
            SpanStyle(
                color = highlight.color,
                fontWeight = if (highlight.bold) FontWeight.Bold else null,
            ),
            start,
            end,
        )
    }
}

internal fun List<MarkdownCodeHighlight>.sliceForChunk(
    chunkStart: Int,
    chunkEnd: Int,
): List<MarkdownCodeHighlight> = mapNotNull { highlight ->
    val start = maxOf(highlight.start, chunkStart)
    val end = minOf(highlight.end, chunkEnd)
    if (start >= end) null else highlight.copy(start = start - chunkStart, end = end - chunkStart)
}
