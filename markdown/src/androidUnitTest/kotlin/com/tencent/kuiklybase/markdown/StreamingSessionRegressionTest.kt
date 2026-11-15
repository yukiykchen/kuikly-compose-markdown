package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownStateImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.lexer.MarkdownLexer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamingSessionRegressionTest {
    private fun state(flavour: MarkdownFlavourDescriptor = GFMFlavourDescriptor()) =
        StreamingMarkdownStateImpl(true, flavour, ReferenceLinkHandlerImpl(), diagnosticsEnabled = true)

    @Test
    fun `reset clears document references and frozen snapshot remains independent`() = runBlocking {
        val state = state()
        state.append("[ref]: https://old.example.com\n\n")
        val old = state.finish()
        assertEquals("https://old.example.com", state.referenceLinkHandler.find("[ref]"))
        state.reset()
        state.append("New answer [ref]")
        state.finish()
        assertTrue(state.links.value.isEmpty())
        assertEquals("", state.referenceLinkHandler.find("[ref]"))
        assertEquals("https://old.example.com", old.referenceDefinitions["[ref]"])
        assertTrue(old.renderBlocks.any { it.source.contains("old.example.com") })
    }

    @Test
    fun `reset during parsing is prompt and old worker cannot publish`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val base = GFMFlavourDescriptor()
        val flavour = object : MarkdownFlavourDescriptor by base {
            override fun createInlinesLexer(): MarkdownLexer {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return base.createInlinesLexer()
            }
        }
        val state = state(flavour)
        val worker = async(Dispatchers.Default) {
            runCatching { state.append("# Old answer\n\n[ref]: https://old.example.com\n\n") }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            state.reset()
            assertTrue(state.snapshot.value.blocks.isEmpty())
        } finally {
            release.countDown()
        }
        assertTrue(worker.await().exceptionOrNull() is CancellationException)
        assertEquals("", state.content.toString())
        assertTrue(state.stableBlocks.value.isEmpty())
        assertTrue(state.snapshot.value.blocks.isEmpty())
        state.append("New answer")
        assertTrue(state.finish().renderBlocks.any { it.source.contains("New answer") })
    }

    @Test
    fun `finish retains every unchanged stable slot and is idempotent`() = runBlocking {
        val state = state()
        state.append((1..40).joinToString("\n\n", postfix = "\n\nTail") { "# Heading $it" })
        val before = state.stableBlockSlots.value.associate { it.id to it.value }
        val final = state.finish()
        before.forEach { (id, value) ->
            assertSame(value, state.stableBlockSlots.value.first { it.id == id }.value)
        }
        assertSame(final, state.finish())
        assertFailsWith<IllegalStateException> { state.append("must not append after finish") }
        Unit
    }

    @Test
    fun `empty chunk does not flush pending CR before LF arrives`() = runBlocking {
        val state = state()
        state.append("first\r")
        state.append("")
        state.append("\nsecond")
        state.finish()
        assertEquals("first\nsecond", state.content.toString())
    }

    @Test
    fun `work budget rejects oversized tail without corrupting published document`() = runBlocking {
        val state = StreamingMarkdownStateImpl(
            true, GFMFlavourDescriptor(), ReferenceLinkHandlerImpl(),
            limits = StreamingMarkdownLimits(maxDocumentCharacters = 1000, maxUnstableCharacters = 32),
        )
        val before = state.append("# Heading\n\n")
        val textBefore = state.content.toString()
        assertFailsWith<IllegalArgumentException> { state.append("a".repeat(100)) }
        assertSame(before, state.snapshot.value)
        assertEquals(textBefore, state.content.toString())
        assertTrue(state.error.value is IllegalArgumentException)
        state.reset()
        state.append("Small answer")
        assertTrue(state.finish().finalized)
    }

    @Test
    fun `upstream failure retains accepted text and is observable without crashing collector`() = runBlocking {
        val state = state()
        state.collectStream(flow {
            emit("# Partial answer\n")
            error("network disconnected")
        })
        assertEquals("# Partial answer\n", state.content.toString())
        assertTrue(state.snapshot.value.finalized)
        assertEquals("network disconnected", state.error.value?.message)
        state.reset()
        assertEquals(null, state.error.value)
    }

    @Test
    fun `reset prevents an old flow collector from feeding the new session`() = runBlocking {
        val state = state()
        val collecting = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val worker = async {
            runCatching {
                state.collectStream(flow {
                    collecting.complete(Unit)
                    release.await()
                    emit("Old stream must not enter new session")
                })
            }
        }
        collecting.await()
        state.reset()
        state.append("New answer")
        release.complete(Unit)
        assertTrue(worker.await().exceptionOrNull() is CancellationException)
        assertEquals("New answer", state.content.toString())
        assertTrue(state.finish().finalized)
    }

    @Test
    fun `captured render list stays immutable while additional blocks freeze`() = runBlocking {
        val state = state()
        val captured = state.append("# First\n\nTail")
        val sources = captured.renderBlocks.map { it.source }
        state.append("\n\n# Second\n\nNew tail")
        state.finish()
        assertEquals(sources, captured.renderBlocks.map { it.source })
    }
}
