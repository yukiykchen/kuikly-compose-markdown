package com.tencent.kuiklybase.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.wrapContentHeight
import com.tencent.kuikly.compose.material3.Button
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.shadow
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalConfiguration
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownState
import com.tencent.kuiklybase.markdown.model.rememberStreamingMarkdownState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

@Page("demo")
class DemoPage : ComposeContainer() {

    private var streamVersion by mutableStateOf(0)

    override fun created() {
        super.created()
        setContent {
            Column(modifier = Modifier.fillMaxSize()) {
                TitleBar()
                MarkdownControl()
                MarkdownComponent()
            }
        }
    }

    @Composable
    private fun TitleBar() {
        val statusBarHeight = LocalConfiguration.current.statusBarHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(Color.LightGray)
                .shadow(2.dp)
        ) {
            Spacer(modifier = Modifier.height(statusBarHeight.dp).fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Markdown")
            }
        }
    }

    @Composable
    private fun MarkdownControl() {
        Row(modifier = Modifier.padding(12.dp)) {
            Button(
                modifier = Modifier.weight(1F),
                onClick = {
                    streamVersion++
                }
            ) {
                Text("ClickMe")
            }
        }
    }


    @Composable
    private fun MarkdownComponent() {
        val version = streamVersion
        val markdownState: StreamingMarkdownState = rememberStreamingMarkdownState()

        LaunchedEffect(version) {
            markdownState.reset()
            markdownChunks(version).collect { chunk -> markdownState.append(chunk) }
            markdownState.finish()
        }

        Column {
            ThemedMarkdown(
                modifier = Modifier.padding(12.dp),
                streamingMarkdownState = markdownState,
            )
        }
    }

    /** Emits append-only chunks, mirroring upstream `Flow<String>` streaming APIs. */
    private fun markdownChunks(version: Int): Flow<String> = flow {
        check(version >= 0)
        val markdown = withContext(Dispatchers.IO) { getAssets("sample.md") }
        var index = 0
        while (index < markdown.length) {
            val end = minOf(index + STREAM_CHUNK_LENGTH, markdown.length)
            emit(markdown.substring(index, end))
            index = end
            delay(STREAM_INTERVAL_MS)
        }
    }

    private companion object {
        const val STREAM_CHUNK_LENGTH = 64
        const val STREAM_INTERVAL_MS = 33L
    }
}
