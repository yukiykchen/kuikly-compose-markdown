package com.tencent.kuiklybase.markdown.model

/** Plain counters for successful compositions; no snapshot state is written. */
class RecompositionCounters {
    var screenCompositions: Int = 0
        private set

    var stableRegionCompositions: Int = 0
        private set

    var activeTailCompositions: Int = 0
        private set

    var stableBlockCompositions: Int = 0
        private set

    var activeTailBlockCompositions: Int = 0
        private set

    var stableBatchCompositions: Int = 0
        private set

    fun recordScreen() {
        screenCompositions++
    }

    fun recordStableRegion() {
        stableRegionCompositions++
    }

    fun recordActiveTail() {
        activeTailCompositions++
    }

    fun recordStableBlock() {
        stableBlockCompositions++
    }

    fun recordStableBatch() {
        stableBatchCompositions++
    }

    fun recordActiveTailBlock() {
        activeTailBlockCompositions++
    }

    /** Starts a new replay/session measurement without allocating a new state object. */
    fun reset() {
        screenCompositions = 0
        stableRegionCompositions = 0
        activeTailCompositions = 0
        stableBlockCompositions = 0
        activeTailBlockCompositions = 0
        stableBatchCompositions = 0
    }

    fun snapshot(): String =
        "screen=$screenCompositions stableRegion=$stableRegionCompositions " +
            "activeTail=$activeTailCompositions stableBlocks=$stableBlockCompositions " +
            "tailBlocks=$activeTailBlockCompositions stableBatches=$stableBatchCompositions"
}
