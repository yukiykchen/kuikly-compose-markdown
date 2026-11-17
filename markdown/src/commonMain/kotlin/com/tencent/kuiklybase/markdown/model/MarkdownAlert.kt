package com.tencent.kuiklybase.markdown.model

import androidx.compose.runtime.Immutable
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode

/** The five GitHub alert markers supported by GFM. */
enum class MarkdownAlertType(val marker: String, val title: String) {
    NOTE("[!NOTE]", "Note"),
    TIP("[!TIP]", "Tip"),
    IMPORTANT("[!IMPORTANT]", "Important"),
    WARNING("[!WARNING]", "Warning"),
    CAUTION("[!CAUTION]", "Caution");

    companion object {
        fun from(markerText: String): MarkdownAlertType? {
            val trimmed = markerText.trim()
            return values().firstOrNull { it.marker.equals(trimmed, ignoreCase = true) }
        }
    }
}

/** Resolves an ALERT node's marker type, using type names for older parser forks. */
fun ASTNode.findAlertType(content: String): MarkdownAlertType? {
    val titleNode = children.firstOrNull { it.type.name == "ALERT_TITLE" } ?: return null
    return MarkdownAlertType.from(titleNode.getTextInNode(content).toString())
}

@Immutable
interface MarkdownAlertColors {
    val note: Color
    val tip: Color
    val important: Color
    val warning: Color
    val caution: Color

    fun colorFor(type: MarkdownAlertType): Color = when (type) {
        MarkdownAlertType.NOTE -> note
        MarkdownAlertType.TIP -> tip
        MarkdownAlertType.IMPORTANT -> important
        MarkdownAlertType.WARNING -> warning
        MarkdownAlertType.CAUTION -> caution
    }
}

@Immutable
private data class DefaultMarkdownAlertColors(
    override val note: Color,
    override val tip: Color,
    override val important: Color,
    override val warning: Color,
    override val caution: Color,
) : MarkdownAlertColors

fun markdownAlertColors(
    note: Color = Color(0xFF0969DA),
    tip: Color = Color(0xFF1A7F37),
    important: Color = Color(0xFF8250DF),
    warning: Color = Color(0xFF9A6700),
    caution: Color = Color(0xFFCF222E),
): MarkdownAlertColors = DefaultMarkdownAlertColors(note, tip, important, warning, caution)

@Immutable
interface MarkdownAlertDimens {
    val barThickness: Dp
    val iconSize: Dp
}

@Immutable
private data class DefaultMarkdownAlertDimens(
    override val barThickness: Dp,
    override val iconSize: Dp,
) : MarkdownAlertDimens

fun markdownAlertDimens(
    barThickness: Dp = 4.dp,
    iconSize: Dp = 16.dp,
): MarkdownAlertDimens = DefaultMarkdownAlertDimens(barThickness, iconSize)

@Immutable
interface MarkdownAlertPadding {
    val container: PaddingValues
    val content: PaddingValues
    val bar: PaddingValues
    val iconSpacing: Dp
    val titleSpacing: Dp
}

@Immutable
private data class DefaultMarkdownAlertPadding(
    override val container: PaddingValues,
    override val content: PaddingValues,
    override val bar: PaddingValues,
    override val iconSpacing: Dp,
    override val titleSpacing: Dp,
) : MarkdownAlertPadding

fun markdownAlertPadding(
    container: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
    content: PaddingValues = PaddingValues(vertical = 4.dp),
    bar: PaddingValues = PaddingValues(start = 2.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
    iconSpacing: Dp = 8.dp,
    titleSpacing: Dp = 4.dp,
): MarkdownAlertPadding = DefaultMarkdownAlertPadding(
    container, content, bar, iconSpacing, titleSpacing,
)

/** Default alert title style; consumers may override this through MarkdownTypography. */
fun defaultAlertTitleStyle(text: TextStyle): TextStyle =
    text.copy(fontWeight = FontWeight.Bold)
