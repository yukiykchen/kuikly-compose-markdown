package com.tencent.kuiklybase.markdown.compose

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.lazy.LazyListState

/** Platform-specific cache-window support; Kuikly 2.30 and OHOS/KBA expose different APIs. */
@Composable
internal expect fun rememberMarkdownCacheWindowListState(
    aheadFraction: Float,
    behindFraction: Float,
): LazyListState

@Composable
fun rememberStreamingMarkdownListState(
    aheadFraction: Float = 1.0f,
    behindFraction: Float = 1.0f,
): LazyListState = rememberMarkdownCacheWindowListState(aheadFraction, behindFraction)
