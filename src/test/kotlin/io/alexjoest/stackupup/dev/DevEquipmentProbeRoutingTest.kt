package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DevEquipmentProbeRoutingTest {
    @Test
    fun `equipment focuses are explicit and excluded from default compat matrix`() {
        assertEquals(
            setOf(
                CORE_INVENTORY_FOCUS,
                EQUIPMENT_OFF_FOCUS,
                EQUIPMENT_OFF_BLACKLIST_FOCUS,
                EQUIPMENT_ON_FOCUS,
                EQUIPMENT_BLACKLIST_FOCUS,
                AE2_OWNER_OFF_FOCUS,
                AE2_OWNER_BLACKLIST_FOCUS,
            ),
            focusedProbeIds,
        )
        for (focus in listOf(EQUIPMENT_OFF_FOCUS, EQUIPMENT_OFF_BLACKLIST_FOCUS, EQUIPMENT_ON_FOCUS, EQUIPMENT_BLACKLIST_FOCUS)) {
            validateFocusedRequest(focus, false, emptySet())
            assertEquals(focus, focusedProbe(focus).id)
            assertFalse(focus in DevCompatProbeRunner.probeIds())
            assertThrows(IllegalArgumentException::class.java) { validateFocusedRequest(focus, true, emptySet()) }
            assertThrows(IllegalArgumentException::class.java) { validateFocusedRequest(focus, false, setOf("inv_wrapper_limit")) }
        }
        assertThrows(IllegalStateException::class.java) { focusedProbe("equipment_typo") }
    }

    @Test
    fun `ownership comparison respects actual package boundaries`() {
        val packages = listOf("io.alexjoest.stackupup.dev")
        assertTrue(ownsFixtureClass(packages, "io.alexjoest.stackupup.dev.DevEquipmentHandsFixture"))
        assertTrue(ownsFixtureClass(packages, "io.alexjoest.stackupup.dev.child.Hands"))
        assertFalse(ownsFixtureClass(packages, "io.alexjoest.stackupup.developer.Hands"))
        assertFalse(ownsFixtureClass(packages, "net.minecraftforge.items.wrapper.EntityHandsInvWrapper"))
        assertFalse(ownsFixtureClass(emptyList(), "io.alexjoest.stackupup.dev.DevEquipmentHandsFixture"))
    }
}
