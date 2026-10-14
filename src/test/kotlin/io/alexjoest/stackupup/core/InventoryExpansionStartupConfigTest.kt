package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class InventoryExpansionStartupConfigTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun missingConfigDefaultsToDisabled() {
        val configFile = temporaryDirectory.resolve("stackupup.cfg").toFile()

        assertFalse(InventoryExpansionStartupConfig.loadEnabled(configFile))
    }

    @Test
    fun malformedBooleanFailsFast() {
        val configFile = File(temporaryDirectory.toFile(), "stackupup.cfg")
        configFile.writeText("compat {\n    inventoryexpansion {\n        B:enabled=maybe\n    }\n}\n")

        assertThrows(IllegalStateException::class.java) {
            InventoryExpansionStartupConfig.loadEnabled(configFile)
        }
    }

    @Test
    fun configuredEnabledValueIsReadFromRestartSnapshotFile() {
        val configFile = File(temporaryDirectory.toFile(), "stackupup.cfg")
        configFile.writeText(
            "compat {\n    inventoryexpansion {\n        B:enabled=true\n    }\n}\n",
        )

        assertTrue(InventoryExpansionStartupConfig.loadEnabled(configFile))
        val policy = InventoryExpansionStartupConfig.load(configFile)
        assertTrue(policy.enabled)
        assertTrue(policy.capacityAllowed)
    }

    @Test
    fun configuredBlacklistKeepsCapacityEnabledBeforeRegistration() {
        val configFile = File(temporaryDirectory.toFile(), "stackupup.cfg")
        configFile.writeText(
            "compat {\n" +
                "    inventoryexpansion {\n" +
                "        B:enabled=true\n" +
                "    }\n" +
                "    inventorystacklimitblacklist {\n" +
                "        S:modIds <\n" +
                "            farmersdelight\n" +
                "        >\n" +
                "    }\n" +
                "}\n",
        )

        val policy = InventoryExpansionStartupConfig.load(configFile)
        assertEquals(listOf("farmersdelight"), policy.modIds)
        assertTrue(policy.enabled)
        assertTrue(policy.capacityAllowed)
    }

    @Test
    fun malformedBlacklistFailsFast() {
        val configFile = File(temporaryDirectory.toFile(), "stackupup.cfg")
        configFile.writeText(
            "compat {\n    inventorystacklimitblacklist {\n        S:modIds <\n            invalid mod id\n        >\n    }\n}\n",
        )

        assertThrows(IllegalStateException::class.java) {
            InventoryExpansionStartupConfig.load(configFile)
        }
    }
}
