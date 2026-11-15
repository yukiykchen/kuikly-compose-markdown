package com.tencent.kuiklybase.markdown

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import com.tencent.kuikly.compose.ui.text.SpanStyle
import com.tencent.kuikly.compose.ui.text.TextLinkStyles
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuiklybase.markdown.annotator.DefaultAnnotatorSettings
import com.tencent.kuiklybase.markdown.annotator.cachedMarkdownAnnotatedString
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownRenderCache
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownAnnotator
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownAnnotatorConfig
import com.tencent.kuiklybase.markdown.model.MarkdownRenderCache
import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals

class RichTextInvalidationTest {
    @Test
    fun `late reference invalidates rich text without parent state changes`() = runBlocking {
        val clock = BroadcastFrameClock()
        withContext(clock) {
            val recomposer = Recomposer(coroutineContext)
            val runner = launch { recomposer.runRecomposeAndApplyChanges() }
            val composition = Composition(object : AbstractApplier<Unit>(Unit) {
                override fun insertTopDown(index: Int, instance: Unit) = Unit
                override fun insertBottomUp(index: Int, instance: Unit) = Unit
                override fun remove(index: Int, count: Int) = Unit
                override fun move(from: Int, to: Int, count: Int) = Unit
                override fun onClear() = Unit
            }, recomposer)
            val handler = ReferenceLinkHandlerImpl()
            val cache = MarkdownRenderCache()
            val settings = DefaultAnnotatorSettings(
                TextLinkStyles(), SpanStyle(),
                DefaultMarkdownAnnotator(null, null, DefaultMarkdownAnnotatorConfig()), handler,
            )
            val node = MarkdownParser(GFMFlavourDescriptor(), cancellationToken = CancellationToken.NonCancellable)
                .buildMarkdownTreeFromString("[ref]" as CharSequence).children.first()
            var rendered = ""
            try {
                composition.setContent {
                    CompositionLocalProvider(LocalMarkdownRenderCache provides cache) {
                        rendered = cachedMarkdownAnnotatedString("[ref]", node, TextStyle.Default, settings).text
                    }
                }
                assertEquals("[ref]", rendered)
                handler.store("[ref]", "https://new.example.com")
                cache.clear()
                Snapshot.sendApplyNotifications()
                repeat(5) {
                    delay(10)
                    clock.sendFrame(System.nanoTime())
                }
                assertEquals("ref", rendered)
            } finally {
                composition.dispose()
                recomposer.close()
                runner.join()
            }
        }
    }
}
