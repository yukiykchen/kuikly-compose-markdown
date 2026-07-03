package com.tencent.news.markdown.qqnews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.text.InlineTextContent
import com.tencent.kuikly.compose.foundation.text.appendInlineContent
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.layout.onGloballyPositioned
import com.tencent.kuikly.compose.ui.layout.positionOnScreen
import com.tencent.kuikly.compose.ui.text.AnnotatedString
import com.tencent.kuikly.compose.ui.text.Placeholder
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.news.core.compose.view.markdown.utils.MARKDOWN_TAG_REF_URL
import com.tencent.news.core.compose.view.markdown.utils.getUnescapedTextInNode
import com.tencent.news.markdown.annotator.AnnotatorSettings
import com.tencent.news.markdown.annotator.LocalUriHandler
import com.tencent.news.markdown.model.MarkdownAnnotator
import com.tencent.news.markdown.model.MarkdownAnnotatorConfig
import com.tencent.news.markdown.model.MarkdownState
import com.tencent.news.markdown.model.markdownAnnotatorConfig
import kotlinx.coroutines.launch
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType

/**
 * 自定义的 Markdown 注解器，用于在注解过程中拦截 [1](@ref) 格式的链接。
 */
class MarkdownRefAnnotator : MarkdownAnnotator {

    override val config: MarkdownAnnotatorConfig = markdownAnnotatorConfig()

    override val inlineContent: (() -> Map<String, InlineTextContent>) = {
        mapOf(MARKDOWN_TAG_REF_URL to createRefInlineTextContent())
    }

    override val annotate: (AnnotatedString.Builder.(content: String, child: ASTNode, annotatorSettings: AnnotatorSettings) -> Boolean) =
        { content, node, annotatorSettings ->
            val refList: List<String> = node.parent?.children
                ?.filter { it.findChildOfType(MarkdownElementTypes.LINK_DESTINATION) != null }
                ?.mapNotNull { extractLinkText(it, content) }
                ?: emptyList()
            // 检查是否是内联链接节点
            if (node.type == MarkdownElementTypes.INLINE_LINK) {
                // 检查是否有 @ref 目标
                val linkDestination = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                    ?.getUnescapedTextInNode(content)

                if (linkDestination == "@ref") {
                    // 提取链接文本（数字引用）
                    val linkText = extractLinkText(node, content)

                    if (linkText != null) {
                        // 按逗号分割链接文本并处理每个数字
                        val refNumbers = linkText.split(",").map { it.trim() }

                        // 为每个数字创建一个单独的内联内容
                        refNumbers.forEach { number ->
                            val ref = RefAnnotator(number, refNumbers).encode()
                            appendInlineContent(MARKDOWN_TAG_REF_URL, ref)
                        }
                        true // 已处理此节点
                    } else {
                        false // 无法提取链接文本
                    }
                } else if (linkDestination?.startsWith("@ref:") == true) {
                    val linkText = extractLinkText(node, content)
                    if (linkText != null && refList != null && refList?.contains(linkText) == true) {
                        val ref = RefAnnotator(linkText, refList ?: emptyList()).encode()
                        appendInlineContent(MARKDOWN_TAG_REF_URL, ref)
                    }
                    true
                } else {
                    false // 不是引用链接
                }
            } else {
                false // 不是内联链接
            }
        }

    private fun extractLinkText(node: ASTNode, content: String) =
        node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
            ?.getUnescapedTextInNode(content)
            ?.replace("[", "")
            ?.replace("]", "")


    private fun createRefInlineTextContent(): InlineTextContent {
        return InlineTextContent(Placeholder(width = 20.sp, height = 16.sp)) { ref ->

            val refAnno = RefAnnotator.decode(ref)

            val uriHandler = LocalUriHandler.current
            val scope = rememberCoroutineScope()
            var gCoordinates = Offset.Zero
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(16.dp)
                    .clip(CircleShape)
                    .background(color = QNTheme.colorScheme.bgBlock)
                    .onGloballyPositioned {
                        gCoordinates = it.positionOnScreen()
                    }.clickable {
                        scope.launch {
                            uriHandler.openRef(refAnno.allRelatedRef, gCoordinates.x, gCoordinates.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = refAnno.index,
                    color = QNTheme.colorScheme.t2,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Suppress("ModelClassRule")
private data class RefAnnotator(val index: String, val allRelatedRef: List<String>) {

    fun encode(): String {
        return "$index;${allRelatedRef.joinToString(",") { it }}"
    }

    companion object {
        fun decode(str: String): RefAnnotator {
            val splits = str.split(";")
            val index = splits.firstOrNull() ?: ""
            val refs = splits.lastOrNull() ?: ""
            return RefAnnotator(index, refs.split(","))
        }
    }
}

@Composable
fun rememberMarkdownRefAnnotator(state: MarkdownState): MarkdownAnnotator {
    return remember(state) { MarkdownRefAnnotator() }
}