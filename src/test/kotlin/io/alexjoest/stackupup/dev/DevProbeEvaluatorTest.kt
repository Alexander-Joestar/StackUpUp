package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevProbeEvaluatorTest {
    @Test
    fun `matchAndSufficientContainer_shouldPass`() {
        val result = evaluateProbeResult(
            requestedCount = 128,
            resolvedLimit = 512,
            actualLimit = 512,
            slotLimit = 1024,
            storedCount = 128,
            remainderCount = 0,
        )

        assertEquals(true, result.passed)
        assertEquals(emptyList<String>(), result.reasons)
    }

    @Test
    fun `mismatch_shouldFail`() {
        val result = evaluateProbeResult(
            requestedCount = 128,
            resolvedLimit = 1024,
            actualLimit = 64,
            slotLimit = 1024,
            storedCount = 64,
            remainderCount = 64,
        )

        assertEquals(false, result.passed)
        assertEquals(
            listOf(
                "目标物品的实际上限 64 与规则解析结果 1024 不一致。",
                "目标物品的实际上限仍未突破 64。",
            ),
            result.reasons,
        )
    }

    @Test
    fun `containerBelowDynamicButInsertPasses_shouldPass`() {
        val result = evaluateProbeResult(
            requestedCount = 128,
            resolvedLimit = 512,
            actualLimit = 512,
            slotLimit = 64,
            storedCount = 64,
            remainderCount = 64,
        )

        assertEquals(true, result.passed)
        assertEquals(emptyList<String>(), result.reasons)
    }
}
