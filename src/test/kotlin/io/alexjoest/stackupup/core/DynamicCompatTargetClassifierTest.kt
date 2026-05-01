package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DynamicCompatTargetClassifierTest {
    @Test
    fun `fixedTarget_shouldSkipDynamicClassification`() {
        for (target in FixedCompatTargets.all()) {
            assertEquals(
                DynamicCompatTargetProfile.NONE,
                DynamicCompatTargetClassifier.classify(target),
                "固定目标不应再次进入动态分类: $target",
            )
        }
    }

    @Test
    fun `customInventoryBridge_shouldClassifyAsInventory`() {
        assertEquals(
            DynamicCompatTargetProfile.INVENTORY,
            DynamicCompatTargetClassifier.classify("io.alexjoest.stackupup.core.TestInventoryBridge"),
        )
    }

    @Test
    fun `forgeWrapper_shouldClassifyAsItemHandler`() {
        assertEquals(
            DynamicCompatTargetProfile.ITEM_HANDLER,
            DynamicCompatTargetClassifier.classify("net.minecraftforge.items.wrapper.PlayerInvWrapper"),
        )
    }

    @Test
    fun `slotSubclass_shouldClassifyAsSlot`() {
        assertEquals(
            DynamicCompatTargetProfile.SLOT,
            DynamicCompatTargetClassifier.classify("net.minecraft.inventory.SlotCrafting"),
        )
    }

    @Test
    fun `unrelatedClass_shouldClassifyAsNone`() {
        assertEquals(
            DynamicCompatTargetProfile.NONE,
            DynamicCompatTargetClassifier.classify("java.lang.String"),
        )
    }

    @Test
    fun `targetedClassification_shouldSkipUnmatchedProfiles`() {
        assertEquals(
            DynamicCompatTargetProfile.NONE,
            DynamicCompatTargetClassifier.classify(
                "net.minecraftforge.items.wrapper.PlayerInvWrapper",
                DynamicCompatTargetProfile.INVENTORY,
            ),
        )
        assertEquals(
            DynamicCompatTargetProfile.NONE,
            DynamicCompatTargetClassifier.classify(
                "net.minecraft.inventory.SlotCrafting",
                DynamicCompatTargetProfile.ITEM_HANDLER,
            ),
        )
    }

    @Test
    fun `fixedTargetProbeSubset_shouldDeriveFromSingleDeclaration`() {
        assertArrayEquals(
            arrayOf(
                "org.cyclops.cyclopscore.inventory.SimpleInventory",
                "net.minecraftforge.items.SlotItemHandler",
                "net.minecraftforge.items.wrapper.InvWrapper",
                "net.minecraftforge.items.wrapper.SidedInvWrapper",
                "net.minecraftforge.items.wrapper.CombinedInvWrapper",
                "net.minecraftforge.items.wrapper.RangedWrapper",
            ),
            FixedCompatTargets.probeTargets(),
        )
    }

    @Test
    fun `probeTargets_shouldPreserveFixedTargetOrder`() {
        val allTargets = FixedCompatTargets.all()
        val expected = ArrayList<String>()
        for (target in allTargets) {
            if (target == "org.cyclops.cyclopscore.inventory.SimpleInventory" ||
                target == "net.minecraftforge.items.SlotItemHandler" ||
                target == "net.minecraftforge.items.wrapper.InvWrapper" ||
                target == "net.minecraftforge.items.wrapper.SidedInvWrapper" ||
                target == "net.minecraftforge.items.wrapper.CombinedInvWrapper" ||
                target == "net.minecraftforge.items.wrapper.RangedWrapper"
            ) {
                expected += target
            }
        }

        assertArrayEquals(expected.toTypedArray(), FixedCompatTargets.probeTargets())
    }
}
