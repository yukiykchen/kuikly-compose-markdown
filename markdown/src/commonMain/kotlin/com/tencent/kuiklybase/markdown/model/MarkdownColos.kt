package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuiklybase.markdown.compose.Markdown


@Stable
interface MarkdownColors {
    /** Represents the color used for the text of this [Markdown] component. */
    val text: Color

    /** Represents the color used for the text of code. */
    @Deprecated("Set color via `codeText` textStyle via `MarkdownTypography` instead")
    val codeText: Color

    /** Represents the color used for the text of code. */
    @Deprecated("Set color via `inlineCode` textStyle via `MarkdownTypography` instead")
    val inlineCodeText: Color

    /** Represents the color used for the text of links. */
    @Deprecated("Set color via `linkText` textStyle via `MarkdownTypography` instead")
    val linkText: Color

    /** Represents the color used for the background of code. */
    val codeBackground: Color

    /** Represents the color used for the inline background of code. */
    val inlineCodeBackground: Color

    /** Represents the color used for the color of dividers. */
    val dividerColor: Color

    /** Represents the color used for the text of tables. */
    @Deprecated("Set color via `tableText` textStyle via `MarkdownTypography` instead")
    val tableText: Color

    /** Represents the color used for the background of tables. */
    val tableBackground: Color

    /** Represents the color used for the background of table headers.*/
    val tableHeaderBackground: Color

    /** Represents the color used for the stroke of tables. */
    val tableStroke: Color

    /** Left accent bar of block quotes. [Color.Unspecified] falls back to [MarkdownTypography.quote] text color. */
    val blockQuoteBar: Color

    /** Background behind block quote content. [Color.Unspecified] means transparent. */
    val blockQuoteBackground: Color
}

@Immutable
class DefaultMarkdownColors(
    override val text: Color = Color(0xFF333333),
    @Deprecated("Set color via `codeText` textStyle via `MarkdownTypography` instead")
    override val codeText: Color = Color.Unspecified,
    @Deprecated("Set color via `inlineCode` textStyle via `MarkdownTypography` instead")
    override val inlineCodeText: Color = Color.Unspecified,
    @Deprecated("Set color via `linkText` textStyle via `MarkdownTypography` instead")
    override val linkText: Color = Color.Unspecified,
    override val codeBackground: Color = Color(0xFFF5F5F5),
    override val inlineCodeBackground: Color = codeBackground,
    override val dividerColor: Color = Color(0xFFE0E0E0),
    @Deprecated("Set color via `tableText` textStyle via `MarkdownTypography` instead")
    override val tableText: Color = Color.Unspecified,
    override val tableBackground: Color = Color(0xFFF5F5F5),
    override val tableHeaderBackground: Color = Color(0xFFEEEEEE),
    override val tableStroke: Color = dividerColor,
    override val blockQuoteBar: Color = Color.Unspecified,
    override val blockQuoteBackground: Color = Color.Unspecified,
) : MarkdownColors
