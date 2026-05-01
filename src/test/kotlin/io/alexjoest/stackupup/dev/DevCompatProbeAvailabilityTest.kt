package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevCompatProbeAvailabilityTest {
    @Test
    fun `falseCheck_shouldBeMissing`() {
        assertEquals(
            ProbeAvailability.missing(),
            evaluateProbeAvailability { false },
        )
    }

    @Test
    fun `exception_shouldNotBeSkippedAsMissing`() {
        assertEquals(
            ProbeAvailability.failed("IllegalStateException: broken linkage"),
            evaluateProbeAvailability { throw IllegalStateException("broken linkage") },
        )
    }
}
