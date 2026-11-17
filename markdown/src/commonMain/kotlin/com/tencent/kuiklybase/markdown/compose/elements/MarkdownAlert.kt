package com.tencent.kuiklybase.markdown.compose.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownColors
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownComponents
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownDimens
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownPadding
import com.tencent.kuiklybase.markdown.compose.LocalMarkdownTypography
import com.tencent.kuiklybase.markdown.compose.handleElement
import com.tencent.kuiklybase.markdown.compose.elements.material.MarkdownBasicText
import com.tencent.kuiklybase.markdown.model.MarkdownAlertType
import org.intellij.markdown.MarkdownTokenTypes.Companion.BLOCK_QUOTE
import org.intellij.markdown.MarkdownTokenTypes.Companion.EOL
import org.intellij.markdown.MarkdownTokenTypes.Companion.WHITE_SPACE
import org.intellij.markdown.ast.ASTNode

/** Renders GitHub alerts (`> [!NOTE]`, `> [!WARNING]`, ...) with an accent bar and title. */
@Composable
fun MarkdownAlert(
    content: String,
    node: ASTNode,
    type: MarkdownAlertType,
    title: String = type.title,
    accent: Color = LocalMarkdownColors.current.alertColors.colorFor(type),
    style: TextStyle = LocalMarkdownTypography.current.alertTitle,
) {
    val dimens = LocalMarkdownDimens.current.alert
    val padding = LocalMarkdownPadding.current.alert
    val components = LocalMarkdownComponents.current

    Row(
        modifier = Modifier
            .padding(padding.container)
            .background(LocalMarkdownColors.current.blockQuoteBackground)
    ) {
        Spacer(
            modifier = Modifier
                .padding(padding.bar)
                .width(dimens.barThickness)
                .fillMaxHeight()
                .background(accent)
        )
        Column(modifier = Modifier.padding(padding.content)) {
            MarkdownBasicText(
                text = title,
                style = style,
                modifier = Modifier,
                color = accent,
            )
            Spacer(Modifier.height(padding.titleSpacing))

            var pastTitle = false
            var seenContent = false
            node.children.forEach { child ->
                when {
                    !pastTitle -> if (child.type.name == "ALERT_TITLE") pastTitle = true
                    child.type == EOL || child.type == WHITE_SPACE || child.type == BLOCK_QUOTE -> Unit
                    else -> {
                        if (seenContent) Spacer(Modifier.height(padding.titleSpacing))
                        seenContent = true
                        key(child.startOffset) {
                            handleElement(
                                node = child,
                                components = components,
                                content = content,
                                includeSpacer = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
