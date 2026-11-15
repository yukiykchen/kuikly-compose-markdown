package com.tencent.kuiklybase.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuiklybase.markdown.compose.LazyMarkdownSuccess
import com.tencent.kuiklybase.markdown.compose.Markdown
import com.tencent.kuiklybase.markdown.compose.StreamingMarkdownContent
import com.tencent.kuiklybase.markdown.compose.components.markdownComponents
import com.tencent.kuiklybase.markdown.compose.elements.MarkdownCode
import com.tencent.kuiklybase.markdown.compose.elements.MarkdownCodeFence
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownState
import com.tencent.kuiklybase.markdown.model.rememberStreamingMarkdownState
import kotlinx.coroutines.delay

/** Deterministic, network-free regression entry exercising bare public APIs. */
@Page("markdown_regression")
class MarkdownRegressionPage : ComposeContainer() {
    override fun created() {
        super.created()
        val mode = pageData.params.optString("mode", "live")
        setContent { RegressionContent(mode) }
    }

    @Composable
    private fun RegressionContent(mode: String) {
        val state = rememberStreamingMarkdownState(diagnosticsEnabled = true)
        var status by remember { mutableStateOf("Running") }
        var captured by remember { mutableStateOf<StreamingMarkdownState.Snapshot?>(null) }
        val corpus = remember(mode) {
            buildString {
                repeat(16) { index ->
                    append("# Section $index\n\n")
                    append("Paragraph 中文 **bold** and [ref].\n\n")
                    append("- item one\n- item two\n\n")
                    append("| A | B |\n| - | - |\n| $index | value |\n\n")
                    append("```kotlin\nval index = $index\n```\n\n")
                }
                append("[ref]: https://example.com\n\nEnd of fixture\n")
            }.let { source ->
                when {
                    mode.startsWith("large-code") -> "# Large code\n\n```kotlin\n" + "println(123456)\n".repeat(2500) + "```\n"
                    mode == "reuse" -> source.replace("[ref].", "[ref](https://example.com).")
                        .replace("[ref]: https://example.com\n\n", "")
                    else -> source
                }
            }
        }
        val components = remember(mode) {
            if (mode == "large-code-baseline") {
                markdownComponents(codeFence = { model ->
                    MarkdownCodeFence(model.content, model.node, model.typography.code) { code, language, style ->
                        MarkdownCode(code, language, style, chunkCharacters = Int.MAX_VALUE)
                    }
                })
            } else markdownComponents()
        }
        LaunchedEffect(mode) {
            state.reset()
            if (mode == "snapshot") {
                state.append("# Captured document\n\nCaptured **body**\n")
                captured = state.finish()
                state.reset()
                state.append("# Replacement document\n")
                state.finish()
            } else {
                corpus.chunked(if (mode.startsWith("large-code")) 512 else 96).forEach {
                    state.append(it)
                    delay(50)
                }
                state.finish()
            }
            status = "Done $mode"
            withFrameNanos { }
            withFrameNanos { }
            if (mode == "reuse") {
                val actual = state.recompositionCounters.stableBlockCompositions
                val expected = state.stableBlockSlots.value.size
                KLog.i("MarkdownRegression", "reuse stableCompositions=$actual finalBlocks=$expected")
                check(actual in 1..expected) { "Stable blocks recomposed unnecessarily: $actual > $expected" }
            }
        }
        Column(Modifier.fillMaxSize()) {
            Text(status)
            when (mode) {
                "lazy" -> LazyMarkdownSuccess(state, modifier = Modifier.weight(1f))
                "snapshot" -> captured?.let { LazyMarkdownSuccess(state, it, modifier = Modifier.weight(1f)) }
                "wrapped" -> Markdown(state, modifier = Modifier.weight(1f))
                else -> LazyColumn(Modifier.weight(1f)) {
                    item("answer") { StreamingMarkdownContent(state, components, diagnosticsEnabled = mode == "reuse") }
                }
            }
        }
    }
}
