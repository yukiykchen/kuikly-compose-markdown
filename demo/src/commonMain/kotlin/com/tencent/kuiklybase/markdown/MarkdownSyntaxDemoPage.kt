package com.tencent.kuiklybase.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box as KuiklyBox
import com.tencent.kuikly.compose.foundation.layout.Column as KuiklyColumn
import com.tencent.kuikly.compose.foundation.layout.Row as KuiklyRow
import com.tencent.kuikly.compose.foundation.layout.Spacer as KuiklySpacer
import com.tencent.kuikly.compose.foundation.layout.Arrangement as KuiklyArrangement
import com.tencent.kuikly.compose.foundation.layout.PaddingValues as KuiklyPaddingValues
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize as kuiklyFillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth as kuiklyFillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height as kuiklyHeight
import com.tencent.kuikly.compose.foundation.layout.padding as kuiklyPadding
import com.tencent.kuikly.compose.foundation.layout.wrapContentHeight as kuiklyWrapContentHeight
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn as KuiklyLazyColumn
import com.tencent.kuikly.compose.material3.Button
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.layout.onSizeChanged
import com.tencent.kuikly.compose.ui.platform.LocalConfiguration
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuiklybase.markdown.annotator.LocalUriHandler
import com.tencent.kuiklybase.markdown.annotator.UriHandler
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownColors
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownDimens
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownPadding
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownTypography
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownComponents
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownExtendedSpans
import com.tencent.kuiklybase.markdown.compose.StreamingMarkdownContent
import com.tencent.kuiklybase.markdown.compose.rememberStreamingMarkdownHost
import com.tencent.kuiklybase.markdown.compose.rememberStreamingMarkdownListState
import com.tencent.kuiklybase.markdown.compose.components.markdownComponents
import com.tencent.kuiklybase.markdown.demo.DemoImageLinkHandler
import com.tencent.kuiklybase.markdown.demo.markdownColor
import com.tencent.kuiklybase.markdown.demo.markdownTypography
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownState
import com.tencent.kuiklybase.markdown.model.RecompositionCounters
import com.tencent.kuiklybase.markdown.model.MarkdownColors
import com.tencent.kuiklybase.markdown.model.MarkdownDimens
import com.tencent.kuiklybase.markdown.model.MarkdownExtendedSpans
import com.tencent.kuiklybase.markdown.model.MarkdownPadding
import com.tencent.kuiklybase.markdown.model.MarkdownTypography
import com.tencent.kuiklybase.markdown.model.markdownPadding
import com.tencent.kuiklybase.markdown.model.markdownDimens
import com.tencent.kuiklybase.markdown.model.bufferMarkdownChunks
import com.tencent.kuiklybase.markdown.model.rememberStreamingMarkdownState
import com.tencent.kuiklybase.markdown.model.markdownExtendedSpans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** Full-syntax demo for checking rendering behavior against a long, mixed Markdown document. */
@Page("markdown_syntax")
class MarkdownSyntaxDemoPage : ComposeContainer() {

    private var replayVersion by mutableStateOf(0)
    private var streaming by mutableStateOf(true)
    private var status by mutableStateOf("准备加载测试文档")
    private var snapshot by mutableStateOf<StreamingMarkdownState.Snapshot?>(null)
    private var playing by mutableStateOf(true)

    override fun created() {
        super.created()
        setContent {
            MarkdownSyntaxScreen()
        }
    }

    @Composable
    private fun MarkdownSyntaxScreen() {
        val state = rememberStreamingMarkdownState(diagnosticsEnabled = true)
        val heightSlotCount = remember { mutableIntStateOf(0) }
        val colorScheme = com.tencent.kuiklybase.markdown.demo.DemoTheme.colorScheme
        val freshColors = markdownColor()
        val freshTypography = markdownTypography()
        val freshExtendedSpans = markdownExtendedSpans()
        val colors = remember(colorScheme) { freshColors }
        val typography = remember(colorScheme) { freshTypography }
        val padding = remember { markdownPadding() }
        val dimens = remember { markdownDimens() }
        val extendedSpans = remember { freshExtendedSpans }
        val components = remember { markdownComponents() }
        val uriHandler = remember {
            object : UriHandler {
                override suspend fun openUri(uri: String) = withContext(Dispatchers.Main) {
                    println("openUri: $uri")
                }

                override suspend fun openRef(ref: List<String>, x: Float, y: Float) = withContext(Dispatchers.Main) {
                    println("openRef: $ref")
                }
            }
        }
        val listState = rememberStreamingMarkdownListState(
            aheadFraction = 1.5f,
            behindFraction = 1.5f,
        )
        val version = replayVersion
        val isStreaming = streaming
        val isPlaying = playing
        if (isPlaying) SideEffect { state.recompositionCounters.recordScreen() }
        val hostState = rememberStreamingMarkdownHost(
            markdownState = state,
            scrollState = listState,
            isStreaming = isPlaying,
            autoFollow = false,
            showResumeButton = true,
            resumeLabel = "恢复流式预览",
        )

        LaunchedEffect(version, isStreaming) {
            status = if (isStreaming) "流式播放中" else "一次性渲染"
            snapshot = null
            playing = true
            heightSlotCount.intValue = 0
            state.recompositionCounters.reset()
            state.reset()
            val source = withContext(Dispatchers.IO) { getAssets(TEST_DOCUMENT) }
            if (isStreaming) {
                chunks(source).bufferMarkdownChunks().collect { delta ->
                    state.append(delta)
                    hostState.afterCommit()
                }
            } else {
                state.append(source)
            }
            hostState.finish()
            snapshot = state.finish()
            playing = false
            status = if (isStreaming) "流式播放完成" else "一次性渲染完成"
            println("MarkdownSyntaxDemo recomposition final ${state.recompositionCounters.snapshot()}")
        }

        KuiklyColumn(modifier = Modifier.kuiklyFillMaxSize().background(Color.White)) {
            SyntaxTitleBar(status)
            RecompositionBadge(state, version, isStreaming, isPlaying)
            SyntaxControls(
                streaming = isStreaming,
                onStatic = {
                    streaming = false
                    replayVersion++
                },
                onReplay = {
                    // Replay the stream without changing the streaming/static mode key.
                    replayVersion++
                },
            )
            MarkdownDocument(
                state = state,
                listState = listState,
                heightSlotCount = heightSlotCount,
                components = components,
                colors = colors,
                typography = typography,
                padding = padding,
                dimens = dimens,
                extendedSpans = extendedSpans,
                uriHandler = uriHandler,
            )
        }
    }

    @Composable
    private fun SyntaxTitleBar(status: String) {
        val statusBarHeight = LocalConfiguration.current.statusBarHeight
        KuiklyColumn(
            modifier = Modifier
                .kuiklyFillMaxWidth()
                .kuiklyWrapContentHeight()
                .background(Color(0xFFF3F4F6))
        ) {
            KuiklySpacer(modifier = Modifier.kuiklyHeight(statusBarHeight.dp).kuiklyFillMaxWidth())
            KuiklyColumn(modifier = Modifier.kuiklyPadding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Markdown 全语法测试", color = Color(0xFF111827))
                Text(status, color = Color(0xFF6B7280))
            }
        }
    }

    @Composable
    private fun RecompositionBadge(
        state: StreamingMarkdownState,
        version: Int,
        isStreaming: Boolean,
        isPlaying: Boolean,
    ) {
        var text by remember { mutableStateOf("统计准备中") }
        LaunchedEffect(version, isStreaming, isPlaying) {
            while (isPlaying) {
                text = recompositionLabel(
                    counters = state.recompositionCounters,
                    appendCount = state.diagnostics.value.appendCount,
                    isPlaying = true,
                )
                delay(DIAGNOSTICS_INTERVAL_MS)
            }
            text = recompositionLabel(
                counters = state.recompositionCounters,
                appendCount = state.diagnostics.value.appendCount,
                isPlaying = false,
            )
        }
        KuiklyBox(
            modifier = Modifier
                .kuiklyFillMaxWidth()
                .kuiklyPadding(horizontal = 12.dp)
                .kuiklyWrapContentHeight(),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                text = text,
                color = Color(0xFF075985),
                style = TextStyle(fontSize = 9.sp),
                modifier = Modifier
                    .background(Color(0xE6F0F9FF))
                    .kuiklyPadding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }

    private fun recompositionLabel(
        counters: RecompositionCounters,
        appendCount: Int,
        isPlaying: Boolean,
    ): String = buildString {
        append(if (isPlaying) "流式重组" else "重组完成")
        append(" append=").append(appendCount)
        append(" screen=").append(counters.screenCompositions)
        append(" stable=").append(counters.stableRegionCompositions)
        append(" tail=").append(counters.activeTailCompositions)
        append(" sBlk=").append(counters.stableBlockCompositions)
        append(" tBlk=").append(counters.activeTailBlockCompositions)
    }

    @Composable
    private fun SyntaxControls(
        streaming: Boolean,
        onStatic: () -> Unit,
        onReplay: () -> Unit,
    ) {
        KuiklyRow(
            modifier = Modifier
                .kuiklyFillMaxWidth()
                .kuiklyPadding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = KuiklyArrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier.weight(1F),
                enabled = streaming,
                onClick = onStatic,
            ) {
                Text("一次性渲染")
            }
            Button(
                modifier = Modifier.weight(1F),
                onClick = onReplay,
            ) {
                Text("重新流式播放")
            }
        }
    }

    @Composable
    private fun MarkdownDocument(
        state: StreamingMarkdownState,
        listState: com.tencent.kuikly.compose.foundation.lazy.LazyListState,
        heightSlotCount: androidx.compose.runtime.MutableIntState,
        components: com.tencent.kuiklybase.markdown.compose.components.MarkdownComponents,
        colors: MarkdownColors,
        typography: MarkdownTypography,
        padding: MarkdownPadding,
        dimens: MarkdownDimens,
        extendedSpans: MarkdownExtendedSpans,
        uriHandler: UriHandler,
    ) {
        val viewportHeight = listState.layoutInfo.viewportSize.height.takeIf { it > 0 } ?: 1_800
        val growthStep = maxOf(1, (viewportHeight * 0.75f).toInt())
        val heightSlots = heightSlotCount.intValue
        MarkdownEnvironment(
            components = components,
            colors = colors,
            typography = typography,
            padding = padding,
            dimens = dimens,
            extendedSpans = extendedSpans,
            uriHandler = uriHandler,
        ) {
            KuiklyLazyColumn(
                state = listState,
                modifier = Modifier.kuiklyFillMaxSize(),
                contentPadding = KuiklyPaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = KuiklyArrangement.spacedBy(10.dp),
            ) {
                item(key = "markdown-answer", contentType = "markdown-answer") {
                    KuiklyColumn(
                        modifier = Modifier
                            .kuiklyFillMaxWidth()
                            .kuiklyWrapContentHeight()
                            .onSizeChanged { size ->
                                val slots = (size.height / growthStep).coerceIn(0, MAX_HEIGHT_SLOTS)
                                if (slots != heightSlotCount.intValue) heightSlotCount.intValue = slots
                            },
                    ) {
                        StreamingMarkdownContent(state, components, diagnosticsEnabled = true)
                    }
                }
                // Kuikly clears its pinned contentSize when the item count grows. These zero-height
                // keys keep native scroll bounds in sync with a growing single Markdown answer.
                repeat(heightSlots) { slot ->
                    item(key = "content-height-slot-$slot", contentType = "content-height-slot") {
                        KuiklySpacer(modifier = Modifier.kuiklyFillMaxWidth().kuiklyHeight(0.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun MarkdownEnvironment(
        components: com.tencent.kuiklybase.markdown.compose.components.MarkdownComponents,
        colors: MarkdownColors,
        typography: MarkdownTypography,
        padding: MarkdownPadding,
        dimens: MarkdownDimens,
        extendedSpans: MarkdownExtendedSpans,
        uriHandler: UriHandler,
        content: @Composable () -> Unit,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalUriHandler provides uriHandler,
            LocalMarkdownColors provides colors,
            LocalMarkdownTypography provides typography,
            LocalMarkdownPadding provides padding,
            LocalMarkdownDimens provides dimens,
            LocalMarkdownComponents provides components,
            LocalMarkdownExtendedSpans provides extendedSpans,
            content = content,
        )
    }

    private fun chunks(source: String) = flow {
        var index = 0
        while (index < source.length) {
            val end = minOf(index + STREAM_CHUNK_LENGTH, source.length)
            emit(source.substring(index, end))
            index = end
            delay(STREAM_INTERVAL_MS)
        }
    }

    private companion object {
        const val TEST_DOCUMENT = "markdown_full_syntax.md"
        const val STREAM_CHUNK_LENGTH = 128
        const val STREAM_INTERVAL_MS = 80L
        const val DIAGNOSTICS_INTERVAL_MS = 100L
        const val MAX_HEIGHT_SLOTS = 24
    }
}
