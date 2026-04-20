package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevCompatProbeAvailabilityTest {
    @Test
    fun `可用性检查返回 false 时应视为正常缺失`() {
        assertEquals(
            ProbeAvailability.missing(),
            evaluateProbeAvailability { false }
        )
    }

    @Test
    fun `可用性检查抛出异常时不应再被当作缺失跳过`() {
        assertEquals(
            ProbeAvailability.failed("IllegalStateException: broken linkage"),
            evaluateProbeAvailability { throw IllegalStateException("broken linkage") }
        )
    }
}
