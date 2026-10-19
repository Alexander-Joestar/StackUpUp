package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class CapacityGetterGateAuditTest {
    @Test
    fun allCapacityGetterMixinsUseFailClosedInventoryGate() {
        val paths = listOf(
            "early/CommandGiveMixin.java",
            "early/CommandReplaceItemMixin.java",
            "early/ForgeItemHandlerLimitMixin.java",
            "early/ServerRecipeBookHelperMixin.java",
            "early/VanillaInventoryLimitMixin.java",
            "late/actuallyadditions/TileEntityInventoryBaseMixin.java",
            "late/ae2/AppEngInternalAEInventoryMixin.java",
            "late/ae2/AppEngInternalInventoryMixin.java",
            "late/ae2/AppEngPatternTermMixin.java",
            "late/ae2supergiant/AppEngInternalInventoryMixin.java",
            "late/ae2supergiant/ContainerAdapterMixin.java",
            "late/ae2supergiant/InternalInventoryMixin.java",
            "late/brandonscore/BrandonsCoreInventoryLimitMixin.java",
            "late/colossalchests/ColossalChestsTileMixin.java",
            "late/cyclopscore/SimpleInventoryMixin.java",
            "late/enderio/EnderIOInventorySlotLimitMixin.java",
            "late/enderio/EnderIOMachineInventoryLimitMixin.java",
            "late/enderio/EnderIOSlottedInventoryLimitMixin.java",
            "late/farmersdelight/FarmersDelightInventoryLimitMixin.java",
            "late/ic2/InvSlotMixin.java",
            "late/immersiveengineering/IEInventoryHandlerMixin.java",
            "late/immersiveengineering/IEMachineSlotLimitMixin.java",
            "late/integrateddynamics/TileMechanicalMachineMixin.java",
            "late/limelib/CommonTileInventoryMixin.java",
            "late/mantle/TileInventoryMixin.java",
            "late/nuclearcraft/NuclearCraftTileInventoryLimitMixin.java",
            "late/refinedstorage/NetworkNodeStorageMonitorMixin.java",
            "late/techreborn/RebornCoreInventoryMixin.java",
        )

        assertTrue(paths.size == 28)
        paths.forEach { relativePath ->
            val source = String(
                Files.readAllBytes(
                    Paths.get("src/main/java/io/alexjoest/stackupup/mixin", relativePath),
                ),
                Charsets.UTF_8,
            )
            assertTrue(
                source.contains("StackLimitHooks.getInventoryCompatibilityStackSize(") ||
                    source.contains("StackLimitHooks.getCoreInventoryStackSize("),
                "$relativePath must use the fail-closed capacity gate",
            )
            assertFalse(
                source.contains("StackLimitHooks.getCompatibilityStackSize()"),
                "$relativePath must not bypass the capacity gate",
            )
        }
    }
}
