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

        paths.forEach { relativePath ->
            val source = mixinSource(relativePath)
            assertTrue(
                source.usesCapacityGate(),
                "$relativePath must use the fail-closed capacity gate",
            )
            assertFalse(
                source.contains("StackLimitHooks.getCompatibilityStackSize()"),
                "$relativePath must not bypass the capacity gate",
            )
        }
        assertEquals(
            capacityGateMixinSources(),
            paths.toSet(),
            "清单必须与 src/main/java 下实际调用容量 gate 的 mixin 源逐一对应：新增或删除容量 getter mixin 时同步本清单",
        )
    }

    /**
     * EnderIO 机器容量 mixin 只保留 `AbstractInventoryMachineEntity`，而且它**不是** transformer 的重复路径：
     * `javap -p` 在 `EnderIO-CEu-5.5.1` 上显示该类是 `public abstract class ... extends
     * AbstractMachineEntity`，整条祖先链（`AbstractMachineEntity` → `TileEntityEio` →
     * `com.enderio.core.common.TileEntityBase` → `TileEntity`）**没有任何一环 implements `IInventory`**；
     * 真正桥接 `IInventory` 的是内部类 `AbstractInventoryMachineEntity$InventoryWrapper`，它的
     * `func_70297_j_()` 转调 `this$0.getInventoryStackLimit()`。因此 `isStackLimitTarget` 不会命中
     * `AbstractInventoryMachineEntity`，transformer 不改写它的 `bipush 64`，这个 mixin 是唯一的容量来源。
     * `TileEnchanter`（`implements ISidedInventory`，getter 是 `func_70297_j_()` + `bipush 64`）满足结构化
     * 目标判定，transformer 已在类加载时改写它，所以不再作为 mixin target（否则同一字面量被替换两次）。
     */
    @Test
    fun enderIoMachineLimitMixinShouldTargetOnlyTheAbstractBase() {
        val source = mixinSource("late/enderio/EnderIOMachineInventoryLimitMixin.java")

        assertEquals(
            listOf("crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity"),
            mixinTargets(source),
            "只允许抽象父类目标：该类不 implements IInventory，transformer 不会命中它，" +
                "mixin 是唯一容量来源；而 concrete 且 implements IInventory 的 TileEnchanter 由 transformer 覆盖",
        )
    }

    private fun mixinSource(relativePath: String): String = String(
        Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin", relativePath)),
        Charsets.UTF_8,
    )

    private fun capacityGateMixinSources(): Set<String> {
        val mixinRoot = Paths.get("src/main/java/io/alexjoest/stackupup/mixin")
        return Files.walk(mixinRoot).use { stream ->
            stream
                .filter(Files::isRegularFile)
                .filter { it.toString().endsWith(".java") }
                .map { mixinRoot.relativize(it).toString().replace('\\', '/') }
                .filter { mixinSource(it).usesCapacityGate() }
                .collect(java.util.stream.Collectors.toSet())
        }
    }

    private fun String.usesCapacityGate(): Boolean = contains("StackLimitHooks.getInventoryCompatibilityStackSize(") ||
        contains("StackLimitHooks.getCoreInventoryStackSize(")

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
