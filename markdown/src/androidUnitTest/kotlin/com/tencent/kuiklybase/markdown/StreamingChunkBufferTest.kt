package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.bufferMarkdownChunks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StreamingChunkBufferTest {
    @Test
    fun `buffer batches fast source and flushes final text`() = runBlocking {
        val chunks = (1..100).map { "chunk$it " }
        val batched = chunks.asFlow().bufferMarkdownChunks().toList()
        assertEquals(chunks.joinToString(""), batched.joinToString(""))
        assertTrue(batched.size < chunks.size)
    }

    @Test
    fun `buffer does not drop text while slow consumer parses`() = runBlocking {
        val chunks = (1..100).map { "chunk$it " }
        val collected = mutableListOf<String>()
        chunks.asFlow().bufferMarkdownChunks(windowMillis = 2, maxBatchChars = 20).collect {
            collected.add(it)
            delay(1)
        }
        assertEquals(chunks.joinToString(""), collected.joinToString(""))
    }

    @Test
    fun `upstream error and collector cancellation propagate`() = runBlocking {
        val partial = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            flow<String> { emit("text"); error("network failed") }.bufferMarkdownChunks().collect { partial.add(it) }
        }
        assertEquals(listOf("text"), partial)
        assertFailsWith<CancellationException> {
            withTimeout(10) {
                flow { while (true) { emit("text"); delay(1) } }.bufferMarkdownChunks().toList()
            }
        }
        Unit
    }
}
