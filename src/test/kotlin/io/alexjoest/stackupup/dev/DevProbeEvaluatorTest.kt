package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `boundary_insertNFullAndNPlusOneRejected_shouldPass`() {
        val result = evaluateBoundaryProbe(
            resolvedLimit = 1024,
            actualLimit = 1024,
            storedAfterN = 1024,
            remainderAfterN = 0,
            storedAfterOne = 1024,
            remainderAfterOne = 1,
        )

        assertEquals(true, result.passed)
        assertEquals(emptyList<String>(), result.reasons)
    }

    @Test
    fun `boundary_ruleNotEffective_shouldFail`() {
        val result = evaluateBoundaryProbe(
            resolvedLimit = 64,
            actualLimit = 64,
            storedAfterN = 64,
            remainderAfterN = 0,
            storedAfterOne = 64,
            remainderAfterOne = 1,
        )

        assertEquals(false, result.passed)
        assertTrue(result.reasons.any { it.contains("边界探针规则未生效") })
    }

    @Test
    fun `boundary_nPlusOneNotRejected_shouldFail`() {
        val result = evaluateBoundaryProbe(
            resolvedLimit = 1024,
            actualLimit = 1024,
            storedAfterN = 1024,
            remainderAfterN = 0,
            storedAfterOne = 1025,
            remainderAfterOne = 0,
        )

        assertEquals(false, result.passed)
        assertTrue(result.reasons.any { it.contains("remainder=1") })
        assertTrue(result.reasons.any { it.contains("存量保持 1024") })
    }

    @Test
    fun `boundary_insertNPartial_shouldFail`() {
        val result = evaluateBoundaryProbe(
            resolvedLimit = 1024,
            actualLimit = 1024,
            storedAfterN = 64,
            remainderAfterN = 960,
            storedAfterOne = 64,
            remainderAfterOne = 1,
        )

        assertEquals(false, result.passed)
        assertTrue(result.reasons.any { it.contains("期望全部存入 1024") })
        assertTrue(result.reasons.any { it.contains("期望 remainder=0") })
    }
}
