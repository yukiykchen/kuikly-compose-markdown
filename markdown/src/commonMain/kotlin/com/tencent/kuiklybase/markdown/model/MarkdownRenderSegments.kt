package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.Immutable
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMElementTypes

/**
 * Bounded, non-lazy render segments. Syntax parsing still uses complete Markdown blocks;
 * segmentation only limits how much unchanged composition work shares one stable scope.
 */
@Immutable
data class MarkdownRenderSegment(
    val id: Long,
    val blocks: List<MarkdownRenderBlock>,
    val isolated: Boolean = false,
) {
    val characters: Int = blocks.sumOf { it.source.length }
}

@Immutable
data class MarkdownRenderSegmentBudget(
    val maxCharacters: Int = 6_000,
    val maxBlocks: Int = 8,
) {
    init {
        require(maxCharacters > 0)
        require(maxBlocks > 0)
    }
}

/** Deterministic segmentation: short benign blocks share a segment; heavy blocks stay isolated. */
internal fun List<MarkdownRenderBlock>.asRenderSegments(
    budget: MarkdownRenderSegmentBudget = MarkdownRenderSegmentBudget(),
): List<MarkdownRenderSegment> {
    if (isEmpty()) return emptyList()
    val result = mutableListOf<MarkdownRenderSegment>()
    var current = mutableListOf<MarkdownRenderBlock>()
    var currentCharacters = 0
    var currentIsolated = false

    fun flush() {
        if (current.isEmpty()) return
        result += MarkdownRenderSegment(
            id = current.first().id,
            blocks = current.toList(),
            isolated = currentIsolated,
        )
        current = mutableListOf()
        currentCharacters = 0
        currentIsolated = false
    }

    forEach { block ->
        val isolated = block.needsIsolatedRenderSegment()
        val wouldExceed = currentCharacters + block.source.length > budget.maxCharacters ||
            current.size + 1 > budget.maxBlocks
        if (isolated || currentIsolated || wouldExceed) flush()
        current += block
        currentCharacters += block.source.length
        currentIsolated = isolated
        if (isolated) flush()
    }
    flush()
    return result
}

private fun MarkdownRenderBlock.needsIsolatedRenderSegment(): Boolean {
    val name = type.name
    return type == MarkdownElementTypes.CODE_FENCE ||
        type == MarkdownElementTypes.CODE_BLOCK ||
        type == MarkdownElementTypes.HTML_BLOCK ||
        type == MarkdownElementTypes.IMAGE ||
        type == GFMElementTypes.TABLE ||
        name == "TABLE" ||
        name == "CODE_FENCE" ||
        name == "CODE_BLOCK" ||
        name == "HTML_BLOCK" ||
        name == "IMAGE"
}
