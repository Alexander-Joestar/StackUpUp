package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.core.InventoryExpansionStartupConfig
import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class StackUpUpMixinConnectorTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @BeforeEach
    fun resetExpansionState() {
        resetInventoryExpansionForTesting()
    }

    @AfterEach
    fun restoreExpansionState() {
        resetInventoryExpansionForTesting()
    }

    @Test
    fun enabledQueuesOrdinaryEarlyThenBaselineAndCapacityLanesInOrder() {
        val connector = StackUpUpMixinConnector()
        val queued = queueStartup(connector, enabled = true)
        val expected = listOf(
            StackUpUpIds.EARLY_MIXIN_CONFIG,
            "mixins.stackupup.baseline.early.json",
            "mixins.stackupup.capacity.early.json",
        ) + ordinaryModules(connector).map { it.config } + capacityModules(connector).map { it.config }

        assertEquals(expected, queued)
        assertEquals(queued.size, queued.toSet().size)
    }

    @Test
    fun disabledCapacityKeepsBaselineAndOrdinarySafetyProfiles() {
        val connector = StackUpUpMixinConnector()
        val queued = queueStartup(connector, enabled = false)
        val expected = listOf(
            StackUpUpIds.EARLY_MIXIN_CONFIG,
            "mixins.stackupup.baseline.early.json",
        ) + ordinaryModules(connector).map { it.config }

        assertEquals(expected, queued)
        assertTrue(queued.contains(StackUpUpIds.LATE_IC2_CLASSIC_MIXIN_CONFIG))
        assertTrue(queued.none { it.contains("capacity") })
        assertFalse(InventoryStackLimitHook.isExpansionEnabled())
    }

    @Test
    fun conflictSkipsStartupPolicyButContinuesOrdinaryLateProfiles() {
        val connector = StackUpUpMixinConnector()
        val queued = mutableListOf<String>()

        connector.connectWith(
            conflicts = listOf("StackUp"),
            loadPolicy = { error("conflict must short-circuit startup config loading") },
            isModPresent = { true },
            queueConfiguration = { queued += it },
        )

        assertEquals(ordinaryModules(connector).map { it.config }, queued)
        assertTrue(queued.none { it.contains("capacity") })
        assertFalse(InventoryStackLimitHook.isExpansionEnabled())
    }

    private fun queueStartup(connector: StackUpUpMixinConnector, enabled: Boolean, blacklistedModIds: List<String> = emptyList()): List<String> {
        val configDirectory = File(temporaryDirectory.toFile(), "config")
        configDirectory.mkdirs()
        val lines = mutableListOf(
            "compat {",
            "    inventoryexpansion {",
            "        B:enabled=$enabled",
            "    }",
        )
        if (blacklistedModIds.isNotEmpty()) {
            lines += "    inventorystacklimitblacklist {"
            lines += "        S:modIds <"
            blacklistedModIds.forEach { lines += "            $it" }
            lines += "        >"
            lines += "    }"
        }
        lines += "}"
        val configFile = File(configDirectory, "stackupup.cfg")
        configFile.writeText(lines.joinToString("\n"))

        val queued = mutableListOf<String>()
        connector.connectWith(
            conflicts = emptyList(),
            loadPolicy = { InventoryExpansionStartupConfig.load(configFile) },
            isModPresent = { true },
            queueConfiguration = { queued += it },
        )
        return queued
    }

    private fun ordinaryModules(connector: StackUpUpMixinConnector) = connector.modules + connector.classicModules

    private fun capacityModules(connector: StackUpUpMixinConnector) = connector.capacityModules
}
