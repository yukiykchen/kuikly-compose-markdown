package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuiklybase.markdown.compose.Markdown
import com.tencent.kuiklybase.markdown.model.MarkdownAlertColors
import com.tencent.kuiklybase.markdown.model.markdownAlertColors

private val DEFAULT_HIGHLIGHT_TEXT = Color(0xFF202124)
private val DEFAULT_HIGHLIGHT_BACKGROUND = Color(0xFFFFF44F)

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

    /** Text color of `==highlighted==` spans. */
    val highlightText: Color get() = DEFAULT_HIGHLIGHT_TEXT

    /** Background color of `==highlighted==` spans, rendered by Kuikly 2.30+ RichText spans. */
    val highlightBackground: Color get() = DEFAULT_HIGHLIGHT_BACKGROUND

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

    /** Accent colors of GitHub alert types. */
    val alertColors: MarkdownAlertColors get() = markdownAlertColors()
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
    override val highlightText: Color = DEFAULT_HIGHLIGHT_TEXT,
    override val highlightBackground: Color = DEFAULT_HIGHLIGHT_BACKGROUND,
    override val alertColors: MarkdownAlertColors = markdownAlertColors(),
) : MarkdownColors
