package com.tencent.kuiklybase.markdown.compose.elements

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuiklybase.markdown.utils.findChildOfTypeRecursive
import com.tencent.kuiklybase.markdown.utils.getUnescapedTextInNode
import com.tencent.kuiklybase.markdown.compose.LocalImageLinkHandler
import com.tencent.kuiklybase.markdown.compose.LocalImageTransformer
import com.tencent.kuiklybase.markdown.compose.LocalReferenceLinkHandler
import com.tencent.kuiklybase.markdown.utils.resolveImageAlt
import com.tencent.kuiklybase.markdown.utils.resolveImageLink
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

@Composable
fun MarkdownImage(content: String, node: ASTNode) {

    val link = node.resolveImageLink(content, LocalReferenceLinkHandler.current) ?: return
    val alt = node.resolveImageAlt(content)
    val imageHandler = LocalImageLinkHandler.current

    LocalImageTransformer.current.transform(link, null)?.let { imageData ->
        imageHandler.store(imageData)
        Image(
            painter = imageData.painter,
            contentDescription = alt ?: imageData.contentDescription,
            modifier = Modifier
                .clickable { imageHandler.preview(imageData) }
                .then(imageData.modifier),
            alignment = imageData.alignment,
            contentScale = imageData.contentScale,
            alpha = imageData.alpha,
            colorFilter = imageData.colorFilter,
        )
    }
}
