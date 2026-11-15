package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import org.intellij.markdown.IElementType
import org.intellij.markdown.ast.ASTNode

/** A top-level Markdown block with source offsets local to [source]. */
@Immutable
class MarkdownRenderBlock internal constructor(
    val id: Long,
    val type: IElementType,
    val source: String,
    val nodeContent: String,
    internal val node: OffsetASTNode,
)

/** Rebases a parser AST subtree so its offsets address the block's local source. */
internal class OffsetASTNode(
    private val delegate: ASTNode,
    private val offsetDelta: Int,
    parent: OffsetASTNode? = null,
) : ASTNode {
    override val type: IElementType get() = delegate.type
    override val startOffset: Int get() = delegate.startOffset - offsetDelta
    override val endOffset: Int get() = delegate.endOffset - offsetDelta
    override val parent: ASTNode? = parent
    override val children: List<ASTNode> = delegate.children.map { OffsetASTNode(it, offsetDelta, this) }
}

/** Stable per-block state; parent list changes do not replace the slot object. */
@Stable
class StableBlockSlot internal constructor(
    val id: Long,
    private val state: MutableState<MarkdownRenderBlock>,
) : State<MarkdownRenderBlock> {
    override val value: MarkdownRenderBlock get() = state.value

    internal fun replace(block: MarkdownRenderBlock) {
        state.value = block
    }

    internal companion object {
        fun create(block: MarkdownRenderBlock): StableBlockSlot = StableBlockSlot(
            id = block.id,
            state = mutableStateOf(block, neverEqualPolicy()),
        )
    }
}

internal fun ASTNode.asRenderBlock(
    document: CharSequence,
    id: Long = startOffset.toLong(),
): MarkdownRenderBlock {
    val start = startOffset.coerceIn(0, document.length)
    val end = endOffset.coerceIn(start, document.length)
    // Keep one preceding newline for table/setext/list markers that reference it.
    val prefix = if (start > 0 && document[start - 1] == '\n') 1 else 0
    val source = document.subSequence(start - prefix, end).toString()
    val localNodeStart = prefix + start - startOffset
    return MarkdownRenderBlock(
        id = id,
        type = type,
        source = source,
        nodeContent = source.substring((localNodeStart).coerceIn(0, source.length)),
        node = OffsetASTNode(this, startOffset - prefix),
    )
}

internal fun ASTNode.hasSameTree(other: ASTNode): Boolean =
    type == other.type && startOffset == other.startOffset && endOffset == other.endOffset &&
        children.size == other.children.size &&
        children.indices.all { children[it].hasSameTree(other.children[it]) }

/** Read-only concatenation of two frozen lists, without copying the stable prefix per delta. */
internal class MarkdownBlockList(
    private val stable: List<MarkdownRenderBlock>,
    private val tail: List<MarkdownRenderBlock>,
) : AbstractList<MarkdownRenderBlock>() {
    override val size: Int get() = stable.size + tail.size
    override fun get(index: Int): MarkdownRenderBlock {
        if (index !in 0 until size) throw IndexOutOfBoundsException("index=$index size=$size")
        return if (index < stable.size) stable[index] else tail[index - stable.size]
    }
}
