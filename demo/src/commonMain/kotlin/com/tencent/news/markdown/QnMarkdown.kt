package com.tencent.news.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.news.markdown.annotator.LocalUriHandler
import com.tencent.news.markdown.annotator.UriHandler
import com.tencent.news.markdown.compose.Markdown
import com.tencent.news.markdown.model.MarkdownState
import com.tencent.news.markdown.model.markdownPadding
import com.tencent.news.markdown.qqnews.QnImageLinkHandler
import com.tencent.news.markdown.qqnews.markdownColor
import com.tencent.news.markdown.qqnews.markdownTypography
import com.tencent.news.markdown.qqnews.rememberMarkdownRefAnnotator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun QnMarkdown(
    modifier: Modifier = Modifier,
    state: MarkdownState,
) {

    CompositionLocalProvider(
        LocalUriHandler provides object : UriHandler {
            override suspend fun openUri(uri: String) = withContext(Dispatchers.Main) {
                println("openUri: $uri")
            }

            override suspend fun openRef(ref: List<String>, x: Float, y: Float) = withContext(Dispatchers.Main) {
                println("openRef: $ref, $x, $y")
            }
        },
    ) {

        Markdown(
            state = state,
            modifier = modifier,
            padding = markdownPadding(),
            colors = markdownColor(),
            typography = markdownTypography(),
            annotator = rememberMarkdownRefAnnotator(state),
            imageLinkHandler = QnImageLinkHandler()
        )
    }
}

