package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackUpUpIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DevCoreInventoryProbeTest {
    @Test
    fun `focus is absent by default and explicitly forwarded`() {
        assertEquals("", readSettings { null }.focus)
        val properties = mapOf("${StackUpUpIds.DEV_AUTOMATION_PREFIX}.focus" to CORE_INVENTORY_FOCUS)
        assertEquals(CORE_INVENTORY_FOCUS, readSettings(properties::get).focus)
        assertFalse(CORE_INVENTORY_FOCUS in DevCompatProbeRunner.probeIds())
    }

    @Test
    fun `focus rejects unknown selection and competing requests`() {
        validateFocusedRequest(CORE_INVENTORY_FOCUS, false, emptySet())
        assertThrows(IllegalArgumentException::class.java) { validateFocusedRequest("typo", false, emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { validateFocusedRequest(CORE_INVENTORY_FOCUS, true, emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { validateFocusedRequest(CORE_INVENTORY_FOCUS, false, setOf("inv_wrapper_limit")) }
    }

    @Test
    fun `conservation does not excuse a sixty four capacity regression`() {
        assertThrows(IllegalStateException::class.java) { checkCounts("real_slot", 129, 64, 65, 128) }
        assertThrows(IllegalStateException::class.java) { checkCounts("lost", 129, 128, 0, 128) }
        assertThrows(IllegalStateException::class.java) { checkCounts("duplicated", 129, 128, 2, 128) }
        assertThrows(IllegalStateException::class.java) { checkCounts("negative", 129, 130, -1, 130) }
        assertEquals(
            "real_slot offered=129 storedDelta=128 remainder/source/cursor=1 expectedDelta=128",
            checkCounts("real_slot", 129, 128, 1, 128),
        )
    }
}
