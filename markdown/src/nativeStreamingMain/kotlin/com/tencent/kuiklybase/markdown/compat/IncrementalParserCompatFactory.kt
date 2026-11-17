package com.tencent.kuiklybase.markdown.compat

import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.EmptyStreamingMarkdownFile
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.parser.StreamingMarkdownFile

/** Uses parser 0.7.18's append-only AST; only included in standard (non-OHOS) builds. */
object IncrementalParserCompatFactory : ParserCompatFactory {
    override fun createStreamingFile(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
    ): CompatStreamingFile = NativeIncrementalStreamingFile(
        EmptyStreamingMarkdownFile(flavour, cancellationToken),
    )

    override fun parseMarkdown(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
        content: CharSequence,
    ): ASTNode = createMarkdownParser(flavour, cancellationToken)
        .buildMarkdownTreeFromString(content)

    override fun createMarkdownParser(
        flavour: MarkdownFlavourDescriptor,
        cancellationToken: CancellationToken,
    ): MarkdownParser = MarkdownParser(flavour, cancellationToken = cancellationToken)
}

private class NativeIncrementalStreamingFile(
    private val delegate: StreamingMarkdownFile,
) : ASTNode by delegate, CompatStreamingFile {
    override val stableChildren: List<ASTNode> get() = delegate.stableChildren
    override val unstableTail: List<ASTNode> get() = delegate.unstableTail
    override fun append(chunk: CharSequence) {
        delegate.append(chunk)
    }
}
