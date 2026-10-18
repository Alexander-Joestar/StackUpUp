package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertEquals
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
            "late/ae2supergiant/AppEngInternalInventoryMixin.java",
            "late/ae2supergiant/ContainerAdapterMixin.java",
            "late/ae2supergiant/InternalInventoryMixin.java",
            "late/brandonscore/BrandonsCoreInventoryLimitMixin.java",
            "late/colossalchests/ColossalChestsTileMixin.java",
            "late/cyclopscore/SimpleInventoryMixin.java",
            "late/enderio/EnderIOInventorySlotLimitMixin.java",
            "late/enderio/EnderIOMachineInventoryLimitMixin.java",
            "late/enderio/EnderIOSlottedInventoryLimitMixin.java",
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

        assertTrue(paths.size == 26)
        paths.forEach { relativePath ->
            val source = mixinSource(relativePath)
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

    /**
     * EnderIO 机器容量 mixin 只保留 `AbstractInventoryMachineEntity`：该类是 `public abstract class`，
     * `InventoryStackLimitTransformer` 跳过抽象类，故 mixin 是该 getter 唯一路径。
     * `TileEnchanter` 的 `getInventoryStackLimit()` 在 concrete 类中只 `return 64;`，transformer 已在类加载时改写，
     * 因此不再作为 mixin target（否则同一字面量被替换两次）。
     */
    @Test
    fun enderIoMachineLimitMixinShouldTargetOnlyTransformerSkippedAbstractBase() {
        val source = mixinSource("late/enderio/EnderIOMachineInventoryLimitMixin.java")

        assertEquals(
            listOf("crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity"),
            mixinTargets(source),
            "只允许抽象父类目标：transformer 跳过抽象类，mixin 是继承该 getter 的具体机器唯一路径" +
                "；concrete 类里只 return 64; 的 TileEnchanter 由 transformer 覆盖",
        )
    }

    private fun mixinSource(relativePath: String): String = String(
        Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin", relativePath)),
        Charsets.UTF_8,
    )

    private fun mixinTargets(source: String): List<String> {
        val start = source.indexOf("@Mixin(")
        assertTrue(start >= 0, "@Mixin 必须存在")
        val end = source.indexOf(')', start)
        assertTrue(end > start, "@Mixin(...) 参数必须闭合")
        return Regex("\"([^\"]+)\"")
            .findAll(source.substring(start, end))
            .map { it.groupValues[1] }
            .toList()
    }
}
