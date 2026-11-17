package com.tencent.kuiklybase.markdown.compat

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser

/** Stable wrapper over parser APIs that differ between 0.7.18 and OHOS 0.7.4. */
interface CompatStreamingFile : ASTNode {
    val stableChildren: List<ASTNode>
    val unstableTail: List<ASTNode>
    fun append(chunk: CharSequence)
}

interface ParserCompatFactory {
    fun createStreamingFile(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
    ): CompatStreamingFile

    fun parseMarkdown(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
        content: CharSequence,
    ): ASTNode

    fun createMarkdownParser(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
    ): MarkdownParser
}

var parserCompatFactory: ParserCompatFactory = createDefaultParserCompatFactory()
    private set

/** Implemented by the parser source set linked into each target. */
internal expect fun createDefaultParserCompatFactory(): ParserCompatFactory

fun installParserCompatFactory(factory: ParserCompatFactory) {
    parserCompatFactory = factory
}

object DefaultParserCompatFactory : ParserCompatFactory {
    override fun createStreamingFile(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
    ): CompatStreamingFile = FullParseStreamingFile(flavour, cancellationToken)

    override fun parseMarkdown(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
        content: CharSequence,
    ): ASTNode = createMarkdownParser(flavour, cancellationToken)
        .buildMarkdownTreeFromString(content.toString())

    override fun createMarkdownParser(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
    ): MarkdownParser = MarkdownParser(flavour, cancellationToken = cancellationToken)
}

/** Full-parse streaming fallback for hosts whose parser fork lacks append-only AST APIs. */
class OhosParserCompatFactory : ParserCompatFactory {
    override fun createStreamingFile(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
    ): CompatStreamingFile = FullParseStreamingFile(flavour, cancellationToken)

    override fun parseMarkdown(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
        content: CharSequence,
    ): ASTNode = createMarkdownParser(flavour, cancellationToken)
        .buildMarkdownTreeFromString(content.toString())

    override fun createMarkdownParser(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
    ): MarkdownParser {
        cancellationToken.checkCancelled()
        return MarkdownParser(flavour)
    }
}

internal class FullParseStreamingFile(
    flavour: MarkdownFlavourDescriptor,
    private val cancellationToken: CancellationToken,
) : CompatStreamingFile {
    private val fullText = StringBuilder()
    private val parser = MarkdownParser(flavour, cancellationToken = cancellationToken)
    private var parsedChildren: List<ASTNode> = emptyList()

    override val type: IElementType get() = MarkdownElementTypes.MARKDOWN_FILE
    override val startOffset: Int get() = 0
    override val endOffset: Int get() = fullText.length
    override val parent: ASTNode? get() = null
    override val children: List<ASTNode> get() = parsedChildren
    override val stableChildren: List<ASTNode> get() = parsedChildren
    override val unstableTail: List<ASTNode> get() = emptyList()

    override fun append(chunk: CharSequence) {
        fullText.append(chunk)
        cancellationToken.checkCancelled()
        parsedChildren = parser.buildMarkdownTreeFromString(fullText.toString()).children
    }
}

fun createStreamingFile(
    flavour: MarkdownFlavourDescriptor,
    cancellationToken: CancellationToken,
): CompatStreamingFile = parserCompatFactory.createStreamingFile(flavour, cancellationToken)

fun parseMarkdown(
    flavour: MarkdownFlavourDescriptor,
    cancellationToken: CancellationToken,
    content: CharSequence,
): ASTNode = parserCompatFactory.parseMarkdown(flavour, cancellationToken, content)

fun createMarkdownParser(
    flavour: MarkdownFlavourDescriptor,
    cancellationToken: CancellationToken,
): MarkdownParser = parserCompatFactory.createMarkdownParser(flavour, cancellationToken)
