package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DevTargetedContainerProbeTest {
    @Test
    fun selector_shouldResolveExactVanillaAndModdedTargets() {
        val chest = DevTargetedContainerSelector.parse("class=$VANILLA_CHEST_CLASS")
        assertEquals("class=$VANILLA_CHEST_CLASS", chest.toString())
        assertEquals("vanilla_chest", resolveTargetedContainerDefinition(chest).id)

        val alloy = DevTargetedContainerSelector.parse("class=$NUCLEARCRAFT_ALLOY_FURNACE_CLASS;modid=NUCLEARCRAFT;capacity=off")
        assertEquals("class=$NUCLEARCRAFT_ALLOY_FURNACE_CLASS;modid=nuclearcraft;capacity=off", alloy.toString())
        assertEquals("off", alloy.capacityMode)
        assertEquals("nuclearcraft_alloy_furnace", resolveTargetedContainerDefinition(alloy).id)
        assertTrue(targetedSelectorMatches(alloy, targetedContainerDefinitions.single { it.id == "nuclearcraft_alloy_furnace" }))
    }

    @Test
    fun targetedFocus_shouldRequireSelectorAndRouteOutsideCompatMatrix() {
        assertEquals(TARGETED_CONTAINER_FOCUS, focusedProbe(TARGETED_CONTAINER_FOCUS).id)
        assertFalse(TARGETED_CONTAINER_FOCUS in DevCompatProbeRunner.probeIds())
        assertThrows(IllegalArgumentException::class.java) {
            validateFocusedRequest(TARGETED_CONTAINER_FOCUS, false, emptySet())
        }
        validateFocusedRequest(
            TARGETED_CONTAINER_FOCUS,
            false,
            emptySet(),
            "class=$VANILLA_CHEST_CLASS",
        )
    }

    @Test
    fun selector_shouldSupportModOnlyAndRejectAmbiguousOrUnknownTargets() {
        assertEquals(
            "nuclearcraft_alloy_furnace",
            resolveTargetedContainerDefinition(DevTargetedContainerSelector.parse("modid:nuclearcraft")).id,
        )
        assertThrows(IllegalStateException::class.java) {
            resolveTargetedContainerDefinition(DevTargetedContainerSelector.parse("modid=minecraft"))
        }
        assertThrows(IllegalStateException::class.java) {
            resolveTargetedContainerDefinition(DevTargetedContainerSelector.parse("class=example.NotAnInventory"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DevTargetedContainerSelector.parse("class=$VANILLA_CHEST_CLASS;class=other.Type")
        }
    }

    @Test
    fun selector_shouldExposeCapacityMixinComparisonState() {
        val definition = targetedContainerDefinitions.single { it.id == "nuclearcraft_alloy_furnace" }
        val off = capacityMixinState(DevTargetedContainerSelector.parse("modid=nuclearcraft;capacity=off"), definition)
        assertEquals("off", off.requested)
        assertTrue(off.status in setOf("DISABLED_BY_CONFIG", "CONFIG_MISMATCH_ON"))
        assertTrue(off.coverage.contains("capacity-late.nuclearcraft.json"))
    }

    @Test
    fun simulationEvidence_shouldKeepActualStorageSeparateFromPrediction() {
        val evidence = evaluateSimulationEvidence(
            offeredCount = 129,
            remainderCount = 65,
            preStackCount = 0,
            postStackCount = 0,
        )
        assertEquals(0, evidence.actualStoredDelta)
        assertEquals(64, evidence.predictedAcceptedCount)
        assertEquals(null, evidence.conservationDelta)
        assertThrows(IllegalArgumentException::class.java) {
            evaluateSimulationEvidence(129, 130, 0, 0)
        }
    }

    @Test
    fun recordFormat_shouldExposeLimitsResultsRemainderConservationAndEvidenceDimensions() {
        val record = DevTargetedContainerRecord(
            selector = "class=$VANILLA_CHEST_CLASS",
            targetId = "vanilla_chest",
            targetClass = VANILLA_CHEST_CLASS,
            targetModId = "minecraft",
            operation = "ordinary_insert",
            slot = 0,
            offeredCount = 129,
            preStackCount = 0,
            postStackCount = 64,
            inventoryLimit = 64,
            slotLimit = 64,
            itemLimit = 128,
            setterResult = null,
            insertResult = "remainder_stack_count=65;simulate=false",
            remainderCount = 65,
            remainderSource = "RETURNED_BY_INSERT",
            conservationDelta = 0,
            serverStorageStatus = "PASS",
            clientSyncDisplayStatus = "UNKNOWN",
            classification = "LEGITIMATE_REMAINDER",
            status = "PASS",
        )

        val machine = record.toMachineJson()
        val human = renderTargetedContainerRecord(record)
        assertTrue(machine.startsWith("{"))
        assertTrue(machine.endsWith("}"))
        assertTrue(machine.contains("\"offeredCount\":129"))
        assertTrue(machine.contains("\"preStackCount\":0"))
        assertTrue(machine.contains("\"postStackCount\":64"))
        assertTrue(machine.contains("\"inventoryLimit\":64"))
        assertTrue(machine.contains("\"slotLimit\":64"))
        assertTrue(machine.contains("\"itemLimit\":128"))
        assertTrue(machine.contains("\"remainderCount\":65"))
        assertTrue(machine.contains("\"conservationDelta\":0"))
        assertTrue(machine.contains("\"serverStorageStatus\":\"PASS\""))
        assertTrue(machine.contains("\"clientSyncDisplayStatus\":\"UNKNOWN\""))
        assertTrue(machine.contains("\"classification\":\"LEGITIMATE_REMAINDER\""))
        assertTrue(human.contains("operation=ordinary_insert"))
        assertTrue(human.contains("serverStorage=PASS"))
        assertTrue(human.contains("clientSyncDisplay=UNKNOWN"))
        assertTrue(human.contains("classification=LEGITIMATE_REMAINDER"))
        assertFalse(human.contains("SERVER_STORAGE_LOSS"))
    }

    @Test
    fun evidenceRenderers_shouldWriteOneMachineAndHumanLinePerRecord() {
        val record = DevTargetedContainerRecord(
            selector = "class=$VANILLA_LARGE_CHEST_CLASS",
            targetId = "vanilla_large_chest",
            targetClass = VANILLA_LARGE_CHEST_CLASS,
            targetModId = "minecraft",
            operation = "target_resolution",
            slot = null,
            offeredCount = null,
            preStackCount = null,
            postStackCount = null,
            inventoryLimit = null,
            slotLimit = null,
            itemLimit = null,
            setterResult = null,
            insertResult = null,
            remainderCount = null,
            remainderSource = "UNKNOWN",
            conservationDelta = null,
            serverStorageStatus = "UNKNOWN",
            clientSyncDisplayStatus = "UNKNOWN",
            classification = "UNKNOWN",
            status = "UNKNOWN",
            detail = "path unavailable",
        )

        assertEquals("${record.toMachineJson()}\n", renderTargetedContainerMachineEvidence(listOf(record)))
        assertEquals("${renderTargetedContainerRecord(record)}\n", renderTargetedContainerHumanEvidence(listOf(record)))
    }
}
