package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ui.text.AnnotatedString
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuiklybase.markdown.annotator.AnnotatorSettings
import org.intellij.markdown.ast.ASTNode
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/** Caches rendered inline text across LazyColumn item disposal/recreation. */
class MarkdownRenderCache {
    private data class Key(
        val type: String,
        val startOffset: Int,
        val endOffset: Int,
        val source: String,
        val style: TextStyle,
        val annotatorSettings: AnnotatorSettings,
        val generation: Int,
    )

    private data class Entry(
        val node: ASTNode,
        val value: AnnotatedString,
    )

    private val entries = mutableMapOf<Key, Entry>()
    private val lock = SynchronizedObject()
    var revision by mutableStateOf(0L)
        private set

    fun getOrPut(
        content: String,
        node: ASTNode,
        style: TextStyle,
        annotatorSettings: AnnotatorSettings,
        build: () -> AnnotatedString,
        generation: Int = 0,
    ): AnnotatedString = synchronized(lock) {
        val end = node.endOffset.coerceAtMost(content.length)
        val start = node.startOffset.coerceIn(0, end)
        val key = Key(
            node.type.name,
            start,
            end,
            content.substring(start, end),
            style,
            annotatorSettings,
            generation,
        )
        val existing = entries[key]
        if (existing != null && existing.node === node) return@synchronized existing.value
        build().also { value ->
            entries[key] = Entry(node, value)
            if (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        }
    }

    fun clear(): Unit = synchronized(lock) {
        entries.clear()
        measuredBlockHeights.clear()
        revision++
        Unit
    }

    private val measuredBlockHeights = mutableMapOf<Long, Int>()

    /** Stable measurement for frozen blocks; px values are density-independent here. */
    fun measuredHeight(blockId: Long): Int? = synchronized(lock) { measuredBlockHeights[blockId] }

    fun rememberMeasuredHeight(blockId: Long, height: Int): Unit = synchronized(lock) {
        measuredBlockHeights[blockId] = height
        if (measuredBlockHeights.size > MAX_ENTRIES) {
            measuredBlockHeights.remove(measuredBlockHeights.keys.first())
        }
        Unit
    }

    private companion object {
        const val MAX_ENTRIES = 512
    }
}
