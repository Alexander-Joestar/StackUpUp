package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackUpUpIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DevAutomationPropertyReaderTest {
    @Test
    fun `newPrefix_shouldOverrideLegacy`() {
        val values =
            mapOf(
                "stackup.dev.autoTest" to "false",
                "stackup.dev.autoTest.mode" to "client",
                "${StackUpUpIds.DEV_AUTOMATION_PREFIX}.enabled" to "true",
                "${StackUpUpIds.DEV_AUTOMATION_PREFIX}.mode" to "server",
                "${StackUpUpIds.DEV_AUTOMATION_PREFIX}.worldFolder" to "stackupup_dev_autotest_new",
            )

        val settings = readSettings(values::get)

        assertTrue(settings.enabled)
        assertEquals("server", settings.mode)
        assertEquals("stackupup_dev_autotest_new", settings.worldFolder)
    }

    @Test
    fun `noNewPrefix_shouldFallbackToLegacy`() {
        val values =
            mapOf(
                "stackup.dev.autoTest" to "true",
                "stackup.dev.autoTest.mode" to "both",
                "stackup.dev.autoTest.item" to "gregtech:meta_item_1",
                "stackup.dev.autoTest.meta" to "516",
            )

        val settings = readSettings(values::get)

        assertTrue(settings.enabled)
        assertEquals("both", settings.mode)
        assertEquals("gregtech:meta_item_1", settings.itemId)
        assertEquals(516, settings.itemMeta)
    }
}
