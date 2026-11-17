package com.tencent.kuiklybase.markdown

import com.tencent.kuiklybase.markdown.model.RecompositionCounters
import kotlin.test.Test
import kotlin.test.assertEquals

class RecompositionCountersTest {
    @Test
    fun `counters expose independent composition scopes`() {
        val counters = RecompositionCounters()
        repeat(7) { counters.recordScreen() }
        repeat(11) { counters.recordActiveTail() }
        repeat(13) { counters.recordStableBlock() }
        counters.recordActiveTailBlock()
        counters.recordStableRegion()
        counters.recordStableBatch()

        val snapshot = counters.snapshot()
        assertEquals(true, "screen=7" in snapshot)
        assertEquals(true, "activeTail=11" in snapshot)
        assertEquals(true, "stableBlocks=13" in snapshot)
        assertEquals(true, "tailBlocks=1" in snapshot)
        assertEquals(true, "stableRegion=1" in snapshot)
        assertEquals(true, "stableBatches=1" in snapshot)
    }

    @Test
    fun `reset clears every composition scope`() {
        val counters = RecompositionCounters()
        repeat(3) { counters.recordScreen() }
        counters.recordStableRegion()
        counters.recordActiveTail()
        counters.recordStableBlock()
        counters.recordActiveTailBlock()
        counters.recordStableBatch()

        counters.reset()

        assertEquals(
            "screen=0 stableRegion=0 activeTail=0 stableBlocks=0 tailBlocks=0 stableBatches=0",
            counters.snapshot(),
        )
    }
}
