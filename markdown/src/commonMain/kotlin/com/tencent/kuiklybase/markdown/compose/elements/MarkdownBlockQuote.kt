package com.tencent.kuiklybase.markdown.compose.elements

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.IntrinsicSize
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownColors
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownComponents
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownDimens
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownPadding
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownTypography
import com.tencent.kuiklybase.markdown.compose.handleElement
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownElementTypes.PARAGRAPH
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType

@Composable
fun MarkdownBlockQuote(
    content: String,
    node: ASTNode,
    style: TextStyle = LocalMarkdownTypography.current.quote,
) {
    val markdownColors = LocalMarkdownColors.current
    val blockQuoteBarColor = markdownColors.blockQuoteBar.takeUnless { it == Color.Unspecified }
        ?: style.color
    val blockQuoteBackground = markdownColors.blockQuoteBackground

    val blockQuoteThickness = LocalMarkdownDimens.current.blockQuoteThickness
    val blockQuote = LocalMarkdownPadding.current.blockQuote
    val blockQuoteText = LocalMarkdownPadding.current.blockQuoteText
    val blockQuoteBar = LocalMarkdownPadding.current.blockQuoteBar
    val markdownComponents = LocalMarkdownComponents.current

    val rowModifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        .then(
            if (blockQuoteBackground != Color.Unspecified) {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(blockQuoteBackground)
            } else {
                Modifier
            },
        )

    Row(modifier = rowModifier) {
        Spacer(
            modifier = Modifier
                .padding(blockQuoteBar)
                .width(blockQuoteThickness)
                .fillMaxHeight()
                .background(blockQuoteBarColor),
        )

        Column(
            modifier = Modifier
                .padding(blockQuote)
                .fillMaxWidth(),
        ) {
            val nonBlockquotes = node.children.filter { it.type != MarkdownElementTypes.BLOCK_QUOTE }
            val nestedQuote = node.findChildOfType(MarkdownElementTypes.BLOCK_QUOTE)

            if (nonBlockquotes.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(blockQuoteText)) {
                    nonBlockquotes.onEach { quote ->
                        when (quote.type) {
                            PARAGRAPH -> {
                                MarkdownParagraph(content, quote, style = style)
                            }

                            else -> {
                                handleElement(
                                    node = quote,
                                    components = markdownComponents,
                                    content = content,
                                    includeSpacer = false
                                )
                            }
                        }
                    }
                }

                if (nestedQuote != null) Spacer(Modifier.height(8.dp))
            }

            if (nestedQuote != null) {
                MarkdownBlockQuote(
                    content = content,
                    node = nestedQuote,
                    style = style
                )
            }
        }
    }
}
