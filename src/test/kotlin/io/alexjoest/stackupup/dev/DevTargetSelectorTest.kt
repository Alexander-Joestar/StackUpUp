package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevTargetSelectorTest {
    @Test
    fun `explicitItem_shouldTakePriority`() {
        val selected = DevTargetRuntimeResolver.selectCandidate(
            explicitItemId = "gregtech:meta_item_1",
            explicitMeta = 11305,
            preferredOreName = "ingotSteel",
            candidates = listOf(
                DevTargetCandidate("gregtech:meta_ingot", 42, setOf("ingotSteel")),
                DevTargetCandidate("gregtech:meta_item_1", 11305, emptySet()),
            ),
        )

        assertEquals(DevTargetCandidate("gregtech:meta_item_1", 11305, emptySet()), selected)
    }

    @Test
    fun `noExplicit_shouldFallbackToGtOreDict`() {
        val selected = DevTargetRuntimeResolver.selectCandidate(
            explicitItemId = "gregtech:gt.metaitem.01",
            explicitMeta = 11305,
            preferredOreName = "ingotSteel",
            candidates = listOf(
                DevTargetCandidate("othermod:steel_ingot", 0, setOf("ingotSteel")),
                DevTargetCandidate("gregtech:meta_ingot", 42, setOf("ingotSteel")),
            ),
        )

        assertEquals(DevTargetCandidate("gregtech:meta_ingot", 42, setOf("ingotSteel")), selected)
    }
}
