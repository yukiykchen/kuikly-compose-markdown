package com.tencent.kuiklybase.markdown.compose

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.ExperimentalFoundationApi
import com.tencent.kuikly.compose.foundation.lazy.LazyListState
import com.tencent.kuikly.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun rememberMarkdownCacheWindowListState(
    aheadFraction: Float,
    behindFraction: Float,
): LazyListState = rememberLazyListState(
    cacheWindow = LazyLayoutCacheWindow(
        aheadFraction = aheadFraction,
        behindFraction = behindFraction,
    ),
)
