package io.alexjoest.stackupup.core

import io.alexjoest.stackupup.StackUpUpCore
import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import net.minecraft.launchwrapper.Launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class InventoryExpansionStartupConfigTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @BeforeEach
    fun resetStartupState() {
        InventoryExpansionStartupConfig.resetForTesting()
        resetInventoryExpansionForTesting()
    }

    @AfterEach
    fun clearStartupState() {
        InventoryExpansionStartupConfig.resetForTesting()
        resetInventoryExpansionForTesting()
    }

    @Test
    fun missingConfigDefaultsToBroadExpansion() {
        val configFile = temporaryDirectory.resolve("stackupup.cfg").toFile()

        assertTrue(InventoryExpansionStartupConfig.loadEnabled(configFile))
        assertTrue(InventoryExpansionStartupConfig.load(configFile).capacityAllowed)
    }

    @Test
    fun existingConfigWithoutSwitchKeepsBroadExpansionDefault() {
        val configFile = File(temporaryDirectory.toFile(), "stackupup.cfg")
        configFile.writeText("compat {\n}\n")

        val policy = InventoryExpansionStartupConfig.load(configFile)

        assertTrue(policy.enabled)
        assertTrue(policy.capacityAllowed)
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
    fun configuredFalseDisablesCapacityAsRestartFallback() {
        val configFile = File(temporaryDirectory.toFile(), "stackupup.cfg")
        configFile.writeText(
            "compat {\n    inventoryexpansion {\n        B:enabled=false\n    }\n}\n",
        )

        val policy = InventoryExpansionStartupConfig.load(configFile)

        assertFalse(policy.enabled)
        assertFalse(policy.capacityAllowed)
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

    @Test
    fun launchPolicyIsCachedUntilRestart() {
        val originalMinecraftHome = Launch.minecraftHome
        val configFile = File(temporaryDirectory.toFile(), "config/${InventoryExpansionStartupConfig.CONFIG_FILE_NAME}")
        configFile.parentFile.mkdirs()
        configFile.writeText("compat {\n    inventoryexpansion {\n        B:enabled=false\n    }\n}\n")
        Launch.minecraftHome = temporaryDirectory.toFile()

        try {
            val startupPolicy = InventoryExpansionStartupConfig.load()
            assertFalse(startupPolicy.enabled)

            configFile.writeText("compat {\n    inventoryexpansion {\n        B:enabled=true\n    }\n}\n")
            assertSame(startupPolicy, InventoryExpansionStartupConfig.load())
            assertFalse(InventoryExpansionStartupConfig.load().enabled)

            InventoryExpansionStartupConfig.resetForTesting()
            assertTrue(InventoryExpansionStartupConfig.load().enabled)
        } finally {
            Launch.minecraftHome = originalMinecraftHome
        }
    }

    @Test
    fun conflictShortCircuitsMalformedLaunchPolicy() {
        val originalMinecraftHome = Launch.minecraftHome
        val disabledProperty = "${StackUpUpIds.MOD_ID}.conflict.disabled"
        val conflictsProperty = "${StackUpUpIds.MOD_ID}.conflict.mods"
        val previousDisabled = System.getProperty(disabledProperty)
        val previousConflicts = System.getProperty(conflictsProperty)
        val configFile = File(temporaryDirectory.toFile(), "config/${InventoryExpansionStartupConfig.CONFIG_FILE_NAME}")
        configFile.parentFile.mkdirs()
        configFile.writeText("compat {\n    inventoryexpansion {\n        B:enabled=invalid\n    }\n}\n")
        Launch.minecraftHome = temporaryDirectory.toFile()
        System.setProperty(disabledProperty, "true")
        System.setProperty(conflictsProperty, "StackUp")

        try {
            assertEquals(0, StackUpUpCore().getASMTransformerClass().size)
            assertFalse(InventoryStackLimitHook.isExpansionEnabled())
        } finally {
            Launch.minecraftHome = originalMinecraftHome
            restoreSystemProperty(disabledProperty, previousDisabled)
            restoreSystemProperty(conflictsProperty, previousConflicts)
        }
    }

    @Test
    fun branchLabelDoesNotReplaceModOrConfigIdentity() {
        assertEquals("asie-was-right", StackUpUpCore.BUILD_LINE)
        assertEquals("stackupup", StackUpUpIds.MOD_ID)
        assertEquals(StackUpUpIds.MOD_ID, StackUpUpIds.CONFIG_ID)
        assertEquals("stackupup.cfg", InventoryExpansionStartupConfig.CONFIG_FILE_NAME)
    }

    private fun restoreSystemProperty(name: String, value: String?) {
        if (value == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, value)
        }
    }
}
