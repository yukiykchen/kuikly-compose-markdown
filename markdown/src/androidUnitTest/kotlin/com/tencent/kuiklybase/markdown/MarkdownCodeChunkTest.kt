package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.compose.elements.splitMarkdownCodeLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownCodeChunkTest {
    @Test
    fun `code chunks preserve lines indentation and empty lines`() {
        val sources = listOf("", "\n", "\n\n", "first\r\nsecond\r\n", "  first\n    second\n\n", "print(1)\n".repeat(2000))
        sources.forEach { source ->
            listOf(1, 17, 4096).forEach { size ->
                assertEquals(source, splitMarkdownCodeLines(source, size).joinToString("\n"))
            }
        }
    }

    @Test
    fun `single long line is not split mid line and completed prefix stays unchanged`() {
        val line = "a".repeat(10000)
        assertEquals(listOf(line), splitMarkdownCodeLines(line, 4096))
        val before = splitMarkdownCodeLines("line\n".repeat(2000), 4096)
        val after = splitMarkdownCodeLines("line\n".repeat(2000) + "new line", 4096)
        assertEquals(before.dropLast(1), after.dropLast(1))
        assertTrue(before.size > 1)
    }
}
