package com.tencent.news.markdown.qqnews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

internal object QNTheme {

    val colorScheme: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalColorScheme.current

}