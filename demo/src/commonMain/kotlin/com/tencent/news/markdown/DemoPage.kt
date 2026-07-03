package com.tencent.news.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.material3.Button
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.shadow
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalConfiguration
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.news.markdown.model.rememberMarkdownState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Page("demo")
class DemoPage : ComposeContainer() {

    private val inputFlow = MutableStateFlow("")

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
        val scope = rememberCoroutineScope()
        Row(modifier = Modifier.padding(12.dp)) {
            Button(
                modifier = Modifier.weight(1F),
                onClick = {
                    scope.launch { fetchMarkdown() }
                }
            ) {
                Text("ClickMe")
            }
        }
    }


    @Composable
    private fun MarkdownComponent() {
        val markdownState = rememberMarkdownState()
        val inputState = inputFlow.collectAsState()
        val input by remember { inputState }

        // 流式加载markdown原文
        LaunchedEffect(Unit) {
            fetchMarkdown()
        }

        // 解析markdown
        LaunchedEffect(input) {
            markdownState.parse(input, false)
        }

        LazyColumn {
            item {
                QnMarkdown(
                    modifier = Modifier.padding(12.dp),
                    state = markdownState
                )
            }
        }
    }


    private suspend fun fetchMarkdown(sse: Boolean = true) = withContext(Dispatchers.IO) {
        val markdown = getAssets("sample.md")
        if (!sse) {
            inputFlow.emit(markdown)
        } else {
            markdown.forEachIndexed { index, c ->
                delay(16)
                inputFlow.emit(markdown.substring(0, index + 1))
            }
        }
    }
}