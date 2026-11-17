package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.Immutable
import com.tencent.kuikly.compose.ui.text.TextLinkStyles
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuiklybase.markdown.model.defaultAlertTitleStyle

interface MarkdownTypography {
    val text: TextStyle
    val code: TextStyle
    val inlineCode: TextStyle
    val h1: TextStyle
    val h2: TextStyle
    val h3: TextStyle
    val h4: TextStyle
    val h5: TextStyle
    val h6: TextStyle
    val quote: TextStyle
    val paragraph: TextStyle
    val ordered: TextStyle
    val bullet: TextStyle
    val list: TextStyle

    @Deprecated("Use textLink instead", ReplaceWith("textLink"))
    val link: TextStyle
    val textLink: TextLinkStyles
    val table: TextStyle

    /** Style of a GitHub alert title. */
    val alertTitle: TextStyle get() = defaultAlertTitleStyle(text)
}

@Immutable
class DefaultMarkdownTypography(
    override val h1: TextStyle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    override val h2: TextStyle = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    override val h3: TextStyle = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    override val h4: TextStyle = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    override val h5: TextStyle = h4,
    override val h6: TextStyle = h4,
    override val text: TextStyle = TextStyle(color = Color(0xFF333333), fontSize = 16.sp, lineHeight = 28.sp),
    override val code: TextStyle = text.copy(fontSize = 14.sp, lineHeight = 22.sp),
    override val inlineCode: TextStyle = code,
    override val quote: TextStyle = text.copy(color = Color(0xFF666666)),
    override val paragraph: TextStyle = text,
    override val ordered: TextStyle = text,
    override val bullet: TextStyle = text,
    override val list: TextStyle = text,
    @Deprecated("Use textLink instead", replaceWith = ReplaceWith("textLink"))
    override val link: TextStyle = text.copy(color = Color(0xFF1769AA)),
    override val textLink: TextLinkStyles = TextLinkStyles(style = link.toSpanStyle()),
    override val table: TextStyle = text,
    override val alertTitle: TextStyle = defaultAlertTitleStyle(text),
) : MarkdownTypography
