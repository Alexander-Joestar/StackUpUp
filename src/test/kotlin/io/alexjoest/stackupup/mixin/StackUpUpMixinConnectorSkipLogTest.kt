package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector
import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StackUpUpMixinConnectorSkipLogTest {
    private companion object {
        const val AE2_CAPACITY_CONFIG = "mixins.stackupup.capacity.late.ae2.json"
        const val AE2_SUPERGIANT_CAPACITY_CONFIG = "mixins.stackupup.capacity.late.ae2supergiant.json"
    }

    @BeforeEach
    fun enableExpansion() {
        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
    }

    @AfterEach
    fun resetExpansion() {
        resetInventoryExpansionForTesting()
    }

    @Test
    fun emptyBlacklist_shouldQueueEachOrdinaryAndCapacityProfileOnce() {
        val connector = StackUpUpMixinConnector()
        val queued = queue(connector)

        assertEquals(registered(connector), queued)
        assertEquals(queued.size, queued.toSet().size, "a known profile must not be registered twice")
        assertTrue(queued.contains(StackUpUpIds.LATE_AE2_MIXIN_CONFIG))
        assertTrue(queued.contains(AE2_CAPACITY_CONFIG))
    }

    @Test
    fun appliedEnergisticsBlacklist_shouldSkipOnlyAe2CapacityProfile() {
        val connector = StackUpUpMixinConnector()
        val queued = queue(connector, setOf(" APPLIEDENERGISTICS2 "))

        assertTrue(queued.contains(StackUpUpIds.LATE_AE2_MIXIN_CONFIG), "ordinary AE2 safety/writer profile must remain queued")
        assertFalse(queued.contains(AE2_CAPACITY_CONFIG), "only the selected AE2 capacity profile may be skipped")
        assertTrue(queued.contains(AE2_SUPERGIANT_CAPACITY_CONFIG), "'ae2' capacity profile is a different mod id and stays queued")
        assertEquals(registered(connector) - AE2_CAPACITY_CONFIG, queued, "only the blacklisted capacity profile may be dropped")
        assertEquals(queued.size, queued.toSet().size)
    }

    @Test
    fun supergiantBlacklist_shouldNotSkipOldAe2CapacityProfile() {
        val connector = StackUpUpMixinConnector()
        val queued = queue(connector, setOf("ae2"))

        assertTrue(queued.contains(StackUpUpIds.LATE_AE2_MIXIN_CONFIG))
        assertTrue(queued.contains(AE2_CAPACITY_CONFIG))
        assertFalse(queued.contains(AE2_SUPERGIANT_CAPACITY_CONFIG))
        assertEquals(registered(connector) - AE2_SUPERGIANT_CAPACITY_CONFIG, queued)
    }

    private fun registered(connector: StackUpUpMixinConnector): List<String> =
        (connector.modules + connector.classicModules + connector.capacityModules).map { it.config }

    private fun queue(connector: StackUpUpMixinConnector, blacklist: Set<String> = emptySet()): List<String> {
        val queued = mutableListOf<String>()
        connector.connectLateWithClassicModules(
            conflicts = emptyList(),
            isModPresent = { true },
            blacklistedModIds = blacklist,
            queueConfiguration = { queued += it },
        )
        return queued
    }
}
