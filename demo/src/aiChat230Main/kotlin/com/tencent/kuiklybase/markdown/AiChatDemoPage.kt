package com.tencent.kuiklybase.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameNanos
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.layout.wrapContentHeight
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Button
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.TextField
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.profiler.RecompositionProfiler
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalConfiguration
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownState
import com.tencent.kuiklybase.markdown.model.rememberStreamingMarkdownState
import com.tencent.kuiklybase.markdown.compose.LocalImageTransformer
import com.tencent.kuiklybase.markdown.compose.LocalImageLinkHandler
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownAnimations
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownAnnotator
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownColors
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownComponents
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownDimens
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownExtendedSpans
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownPadding
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownRenderCache
import com.tencent.kuiklybase.markdown.compose.StreamingMarkdownContent
import com.tencent.kuiklybase.markdown.compose.MarkdownSnapshotContent
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownTypography
import com.tencent.kuiklybase.markdown.compose.LocalReferenceLinkHandler
import com.tencent.kuiklybase.markdown.compose.LocalTextProvider
import com.tencent.kuiklybase.markdown.compose.components.markdownComponents
import com.tencent.kuiklybase.markdown.demo.markdownColor
import com.tencent.kuiklybase.markdown.demo.markdownTypography
import com.tencent.kuiklybase.markdown.model.ImageLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.markdownDimens
import com.tencent.kuiklybase.markdown.model.markdownPadding
import com.tencent.kuiklybase.markdown.model.markdownAnimations
import com.tencent.kuiklybase.markdown.model.markdownAnnotator
import com.tencent.kuiklybase.markdown.model.markdownExtendedSpans
import com.tencent.kuiklybase.markdown.model.NoOpImageTransformerImpl
import com.tencent.kuiklybase.markdown.model.NoOpTextProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Kuikly 2.30 SSE AI chat demo.
 *
 * API_URL / MODEL / API_KEY are supplied through pageData by the host app, so
 * credentials never live in the shared source set. The page uses
 * [NetworkModule.httpStreamRequest] and feeds parsed content deltas into the
 * incremental Markdown state used by the performance demo.
 */
@Page("ai_chat")
class AiChatDemoPage : ComposeContainer() {

    private var messages by mutableStateOf(listOf(ChatMessage(assistant = true, text = DEFAULT_PROMPT)))
    private var input by mutableStateOf(DEFAULT_PROMPT)
    private var isStreaming by mutableStateOf(false)
    private var status by mutableStateOf("Ready")
    private var requestVersion by mutableStateOf(0)
    private val pendingText = StringBuilder()
    private val pendingLock = SynchronizedObject()
    private val streamFinished = MutableStateFlow(false)
    private val pendingLength: Int get() = synchronized(pendingLock) { pendingText.length }
    private var restoredMarkdown: String? = null
    private var debugChunkLogged = false
    private var recompositionProfilerStarted = false
    private var diagnosticsEnabled = false
    private var activeRequest: NetworkModule.StreamRequestHandle? = null
    private var autoFollowOutput = true

    override fun created() {
        super.created()
        diagnosticsEnabled = runCatching {
            pageData.params.optBoolean(PAGE_DATA_DIAGNOSTICS, false)
        }.getOrDefault(false)
        setContent { AiChatScreen() }
    }

    /** Reads the last answer after the Kuikly page has been recreated. */
    private fun loadRestoredMarkdown(): String {
        val embedded = runCatching {
            pageData.params.optString(PAGE_DATA_RESTORED_MARKDOWN)
        }.getOrDefault("")
        if (embedded.isNotEmpty()) return embedded
        return runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .getString(STORAGE_KEY_MARKDOWN)
        }.getOrDefault("")
    }

    private fun saveRestoredMarkdown(markdown: String) {
        restoredMarkdown = markdown
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(STORAGE_KEY_MARKDOWN, markdown)
        }
    }

    @Composable
    private fun AiChatScreen() {
        val listState = rememberLazyListState()
        val markdownState: StreamingMarkdownState = rememberStreamingMarkdownState(
            diagnosticsEnabled = diagnosticsEnabled,
        )
        val components = remember { markdownComponents() }
        val hasAnswer by markdownState.hasRenderableBlocks.collectAsState()
        val contentLength by snapshotFlow { markdownState.contentLength }
            .collectAsState(initial = 0)
        DisposableEffect(Unit) {
            onDispose { activeRequest?.close() }
        }
        // Keep new text visible while streaming, but stop following when the user scrolls up.
        LaunchedEffect(listState) {
            snapshotFlow {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                val lastIndex = info.totalItemsCount - 1
                val bottomDistance = if (last != null && last.index == lastIndex) {
                    info.viewportEndOffset - (last.offset + last.size)
                } else Int.MAX_VALUE
                Triple(bottomDistance, listState.lastScrolledBackward, listState.isScrollInProgress)
            }.collect { (bottomDistance, scrolledBackward, scrolling) ->
                if (scrolledBackward && scrolling) autoFollowOutput = false
                if (bottomDistance in 0..96) autoFollowOutput = true
            }
        }
        LaunchedEffect(contentLength, hasAnswer, autoFollowOutput, isStreaming) {
            if (!autoFollowOutput || !hasAnswer || !isStreaming) return@LaunchedEffect
            withFrameNanos { }
            listState.scroll {
                var remaining = listState.layoutInfo.viewportSize.height.toFloat()
                var attempts = 0
                while (remaining > 0f && attempts++ < 8) {
                    val delta = scrollBy(remaining)
                    if (delta <= 0f) break
                    remaining -= delta
                }
            }
        }
        if (diagnosticsEnabled) {
            SideEffect { markdownState.recompositionCounters.recordScreen() }
        }

        LaunchedEffect(requestVersion) {
            val saved = loadRestoredMarkdown()
            if (requestVersion > 0 && saved.isEmpty()) {
                try {
                    if (runChatStream(markdownState)) {
                        withFrameNanos { }
                        listState.scrollToItem(messages.lastIndex)
                        consumeStreamBuffer(markdownState) {
                            withFrameNanos { }
                            withFrameNanos { }
                            listState.scrollToItem(messages.size + 1)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    status = "Error: ${error.message}"
                } finally {
                    activeRequest?.close()
                    activeRequest = null
                    if (recompositionProfilerStarted) {
                        RecompositionProfiler.stop()
                        recompositionProfilerStarted = false
                    }
                    isStreaming = false
                }
            } else if (saved.isNotEmpty()) {
                markdownState.reset()
                markdownState.append(saved)
                markdownState.finish()
                status = "Completed (restored)"
                restoredMarkdown = saved
            }
        }

        Column(modifier = Modifier.fillMaxSize().background(ChatColors.background)) {
            ChatHeader(status)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message)
                }
                if (isStreaming || hasAnswer) {
                    item(key = "live-answer") {
                        LiveAnswerLabel()
                    }
                    item(key = "markdown-answer") {
                        MarkdownAnswer(
                            markdownState = markdownState,
                            components = components,
                        )
                    }
                }
            }

            ChatComposer(
                value = input,
                enabled = !isStreaming,
                onValueChange = { input = it },
                onSend = {
                    val prompt = input.trim().takeIf { it.isNotEmpty() } ?: return@ChatComposer
                    if (markdownState.hasRenderableBlocks.value) {
                        messages = messages + ChatMessage(
                            assistant = true,
                            text = markdownState.content.toString(),
                            snapshot = markdownState.snapshot.value,
                        )
                    }
                    messages = messages + ChatMessage(assistant = false, text = prompt)
                    markdownState.reset()
                    autoFollowOutput = true
                    saveRestoredMarkdown("")
                    synchronized(pendingLock) { requestVersion++ }
                },
                onStop = {
                    activeRequest?.close()
                    activeRequest = null
                    streamFinished.value = true
                    status = "Stopped"
                },
            )
        }
    }

    private fun runChatStream(markdownState: StreamingMarkdownState): Boolean {
        val config = chatConfig()
        if (config.apiKey.isBlank()) {
            status = "Missing API key in local.properties"
            return false
        }

        val network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        if (network == null) {
            status = "NetworkModule unavailable"
            return false
        }

        val prompt = messages.lastOrNull { !it.assistant }?.text ?: DEFAULT_PROMPT
        val headers = JSONObject()
            .put("Content-Type", "application/json")
            .put("Accept", "text/event-stream")
            .put("Authorization", "Bearer ${config.apiKey}")
        val history = messages.filter { !it.assistant || it.snapshot != null }
            .map { (if (it.assistant) "assistant" else "user") to it.text }
        val body = chatCompletionBody(prompt, config.model, history)
        val version = requestVersion
        synchronized(pendingLock) { pendingText.setLength(0) }
        streamFinished.value = false
        saveRestoredMarkdown("")
        debugChunkLogged = false
        markdownState.reset()
        val parser = SseEventParser(diagnosticsEnabled) { delta ->
            synchronized(pendingLock) {
                if (version == requestVersion && !streamFinished.value) pendingText.append(delta)
            }
        }

        isStreaming = true
        status = "Streaming via Kuikly 2.30 httpStreamRequest"
        if (diagnosticsEnabled) KLog.i(TAG, "request start v=$requestVersion")
        activeRequest = network.httpStreamRequest(
            url = config.apiUrl,
            isPost = true,
            param = body,
            headers = headers,
            timeout = 180,
        ) { event, data, response ->
            if (version != requestVersion || streamFinished.value) return@httpStreamRequest
            when (event) {
                "data" -> {
                    if (diagnosticsEnabled && !debugChunkLogged) {
                        debugChunkLogged = true
                        KLog.i(TAG, "sse sample=${data.take(240).replace("\n", "\\n")}")
                    }
                    if (diagnosticsEnabled) {
                        KLog.i(TAG, "sse data bytes=${data.length} pending=$pendingLength")
                    }
                    parser.feed(data)
                }
                "complete" -> {
                    parser.finish()
                    synchronized(pendingLock) {
                        if (version == requestVersion) {
                            streamFinished.value = true
                            status = "Finishing"
                        }
                    }
                    if (diagnosticsEnabled) KLog.i(TAG, "sse complete pending=$pendingLength")
                }
                "error" -> {
                    parser.finish()
                    synchronized(pendingLock) {
                        if (version == requestVersion) {
                            streamFinished.value = true
                            status = "Error ${response?.statusCode ?: ""}: $data"
                        }
                    }
                    KLog.e(TAG, "sse error status=${response?.statusCode} data=$data")
                }
            }
        }
        return true
    }

    /** Applies buffered deltas on the Compose frame loop, not in the network callback. */
    private suspend fun consumeStreamBuffer(
        markdownState: StreamingMarkdownState,
        onFirstContent: suspend () -> Unit,
    ) {
        if (diagnosticsEnabled) {
            KLog.i(TAG, "consume enter streaming=$isStreaming finished=${streamFinished.value} pending=$pendingLength")
            KLog.i(TAG, "consume loop start")
            configureRecompositionProfiler()
        }
        var lastPublishAt = DateTime.currentTimestamp()
        var firstContentPublished = false
        while (isStreaming || pendingLength > 0 || !streamFinished.value) {
            val now = DateTime.currentTimestamp()
            val tailLength = markdownState.activeTailLength
            val interval = when {
                tailLength >= 65536 -> 200L
                tailLength >= 16384 -> 100L
                else -> PUBLISH_INTERVAL_MS
            }
            if ((pendingLength > 0 && now - lastPublishAt >= interval) || streamFinished.value) {
                val delta = synchronized(pendingLock) {
                    pendingText.toString().also { pendingText.setLength(0) }
                }
                if (delta.isNotEmpty()) {
                    if (diagnosticsEnabled && !recompositionProfilerStarted) {
                        RecompositionProfiler.start()
                        recompositionProfilerStarted = true
                        KLog.i(TAG, "RCProfiler started at first正文 publish")
                    }
                    markdownState.append(delta)
                    if (!firstContentPublished) {
                        firstContentPublished = true
                        onFirstContent()
                    }
                    if (diagnosticsEnabled) {
                        saveRestoredMarkdown(markdownState.content.toString())
                        val metrics = markdownState.diagnostics.value
                        val currentLinksRevision = markdownState.linksRevision.value
                        val counters = markdownState.recompositionCounters
                        KLog.i(
                            TAG,
                            "publish delta=${delta.length} stable=${metrics.stableBlockCount} " +
                                "tail=${metrics.unstableBlockCount} reused=${metrics.reusedStableBlocks} " +
                                "new=${metrics.newStableBlocks} links=$currentLinksRevision " +
                                "recompositions=${counters.snapshot()}",
                        )
                    }
                }
                lastPublishAt = now
            }
            if (streamFinished.value && pendingLength == 0) {
                markdownState.finish()
                saveRestoredMarkdown(markdownState.content.toString())
                isStreaming = false
                if (status == "Finishing") status = "Completed"
                withFrameNanos { }
                withFrameNanos { }
                if (diagnosticsEnabled) {
                    emitRecompositionProfilerReport()
                    val metrics = markdownState.diagnostics.value
                    val currentLinksRevision = markdownState.linksRevision.value
                    val counters = markdownState.recompositionCounters
                    KLog.i(
                        TAG,
                        "finish blocks=${metrics.stableBlockCount} " +
                            "parity=${metrics.finalParseMatches} links=$currentLinksRevision " +
                            "chars=${restoredMarkdown?.length} recompositions=${counters.snapshot()}",
                    )
                }
                break
            }
            delay(FRAME_INTERVAL_MS)
        }
    }

    private fun configureRecompositionProfiler() {
        recompositionProfilerStarted = false
        RecompositionProfiler.configure {
            sampleRate = 1.0f
            hotspotThreshold = 20
            enableStateTracking = true
            includeFrameworkComposables = false
            enableOverlay = false
            enableLog = false
            enableFile = false
            enableBuiltinFilters = true
        }
    }

    private fun emitRecompositionProfilerReport() {
        if (!recompositionProfilerStarted) return
        val report = RecompositionProfiler.getReport(saveToFile = false)
        KLog.i(
            TAG,
            "RCProfiler summary duration=${report.durationMs}ms frames=${report.totalFrames} " +
                "recompositions=${report.totalRecompositions} hotspots=${report.hotspots.size}",
        )
        report.composables
            .filter { stats ->
                stats.name.contains("StableMarkdownBlocks") ||
                    stats.name.contains("RenderMarkdownBlock") ||
                    stats.name.contains("StreamingMarkdownContent") ||
                    stats.name.contains("IncrementalMarkdownDocument") ||
                    stats.name.contains("AiChatScreen")
            }
            .forEach { stats ->
                KLog.i(
                    TAG,
                    "RCProfiler composable=${stats.name}@${stats.sourceLocation} " +
                        "count=${stats.recompositionCount} avg=${stats.avgDurationMs}ms " +
                        "max=${stats.maxDurationMs}ms params=${stats.paramChangeFrequency} " +
                        "states=${stats.triggerStates} scopes=${stats.scopeDistribution}",
                )
            }
        report.stateChanges.take(30).forEach { record ->
            KLog.i(
                TAG,
                "RCProfiler state=${record.stateKey} count=${record.changeCount} " +
                    "first=${record.firstChangeMs}ms last=${record.lastChangeMs}ms",
            )
        }
        RecompositionProfiler.stop()
        recompositionProfilerStarted = false
    }

    private fun chatConfig(): ChatConfig = ChatConfig(
        apiUrl = pageData.params.optString(PAGE_DATA_API_URL, DEFAULT_API_URL),
        apiKey = pageData.params.optString(PAGE_DATA_API_KEY).ifBlank {
            pageData.params.optString("apiKey")
        },
        model = pageData.params.optString(PAGE_DATA_MODEL, DEFAULT_MODEL),
    )

    @Composable
    private fun ChatHeader(statusText: String) {
        val statusBarHeight = LocalConfiguration.current.statusBarHeight
        Column(modifier = Modifier.fillMaxWidth().background(ChatColors.header)) {
            Spacer(modifier = Modifier.height(statusBarHeight.dp).fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI Chat / SSE", color = ChatColors.title)
                Spacer(modifier = Modifier.width(12.dp))
                Text(statusText, color = ChatColors.muted)
            }
        }
    }

    @Composable
    private fun ChatBubble(message: ChatMessage) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.assistant) Arrangement.Start else Arrangement.End,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (message.assistant) 0.88f else 0.78f)
                    .background(
                        if (message.assistant) ChatColors.assistantBubble else ChatColors.userBubble,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(12.dp),
            ) {
                if (message.snapshot != null) {
                    MarkdownSnapshotContent(message.snapshot)
                } else {
                    Text(message.text, color = ChatColors.text)
                }
            }
        }
    }

    @Composable
    private fun LiveAnswerLabel() {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(ChatColors.assistantBubble, RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Text(if (isStreaming) "Assistant / streaming" else "Assistant", color = ChatColors.muted)
        }
    }

    @Composable
    private fun MarkdownAnswer(
        markdownState: StreamingMarkdownState,
        components: com.tencent.kuiklybase.markdown.compose.components.MarkdownComponents,
    ) {
        CompositionLocalProvider(
            LocalReferenceLinkHandler provides markdownState.referenceLinkHandler,
            LocalMarkdownRenderCache provides markdownState.renderCache,
            LocalMarkdownPadding provides markdownPadding(),
            LocalMarkdownDimens provides markdownDimens(),
            LocalMarkdownColors provides markdownColor(),
            LocalMarkdownTypography provides markdownTypography(),
            LocalImageTransformer provides NoOpImageTransformerImpl(),
            LocalImageLinkHandler provides ImageLinkHandlerImpl(),
            LocalTextProvider provides NoOpTextProvider(),
            LocalMarkdownAnnotator provides markdownAnnotator(),
            LocalMarkdownExtendedSpans provides markdownExtendedSpans(),
            LocalMarkdownComponents provides components,
            LocalMarkdownAnimations provides markdownAnimations(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(ChatColors.assistantBubble, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                StreamingMarkdownContent(
                    streamingMarkdownState = markdownState,
                    components = components,
                    diagnosticsEnabled = diagnosticsEnabled,
                )
            }
        }
    }

    @Composable
    private fun ChatComposer(
        value: String,
        enabled: Boolean,
        onValueChange: (String) -> Unit,
        onSend: () -> Unit,
        onStop: () -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(ChatColors.header).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(0.72f).height(52.dp),
                singleLine = true,
                placeholder = { Text("Ask for a long Markdown answer") },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = if (enabled) onSend else onStop) {
                Text(if (enabled) "Send" else "Stop")
            }
        }
    }

    private data class ChatMessage(
        val assistant: Boolean,
        val text: String,
        val snapshot: StreamingMarkdownState.Snapshot? = null,
        val id: String = "$assistant-$text-${nextMessageId++}",
    )

    private data class ChatConfig(val apiUrl: String, val apiKey: String, val model: String)

    private object ChatColors {
        val background = Color(0xFFF6F7F9)
        val header = Color.White
        val title = Color(0xFF1F2937)
        val text = Color(0xFF111827)
        val muted = Color(0xFF6B7280)
        val assistantBubble = Color(0xFFE9EEF7)
        val userBubble = Color(0xFFD7F3DF)
    }

    private companion object {
        const val TAG = "AiChatDemo"
        const val DEFAULT_API_URL = "https://api.moonshot.cn/v1/chat/completions"
        const val DEFAULT_MODEL = "kimi-k2.6"
        const val PAGE_DATA_API_URL = "aiChatApiUrl"
        const val PAGE_DATA_API_KEY = "aiChatApiKey"
        const val PAGE_DATA_MODEL = "aiChatModel"
        const val PAGE_DATA_RESTORED_MARKDOWN = "aiChatRestoredMarkdown"
        const val PAGE_DATA_DIAGNOSTICS = "aiChatDiagnostics"
        const val STORAGE_KEY_MARKDOWN = "ai_chat_last_markdown"
        const val PUBLISH_INTERVAL_MS = 50L
        const val FRAME_INTERVAL_MS = 16L
        const val DEFAULT_PROMPT =
            "Write a long Markdown answer about optimizing streaming UI rendering. " +
                "Include headings, bullet lists, a code block, a comparison table, and a short conclusion."
        var nextMessageId = 0L
    }
}
