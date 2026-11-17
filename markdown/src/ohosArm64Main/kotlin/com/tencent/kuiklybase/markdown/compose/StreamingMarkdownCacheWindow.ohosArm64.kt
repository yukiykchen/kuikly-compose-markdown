package com.tencent.kuiklybase.markdown.compose

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.lazy.LazyListState
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState

/** Kuikly 2.12 OHOS has no LazyLayoutCacheWindow; keep the public API source-compatible. */
@Composable
internal actual fun rememberMarkdownCacheWindowListState(
    aheadFraction: Float,
    behindFraction: Float,
): LazyListState = rememberLazyListState()
