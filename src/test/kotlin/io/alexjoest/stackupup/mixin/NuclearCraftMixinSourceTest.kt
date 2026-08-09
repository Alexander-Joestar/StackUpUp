package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.bootstrap.StackUpUpLateMixinLoader
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class NuclearCraftMixinSourceTest {
    @Test
    fun `nuclearCraftMixin_shouldPatchRealCapacitySourceNotSplitOnlyExpansion`() {
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.late.nuclearcraft.json")), Charsets.UTF_8)
        val mixin = String(
            Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/NuclearCraftTileInventoryLimitMixin.java")),
            Charsets.UTF_8,
        )

        // 真实写入面收敛：四个直接实现 ITileInventory 且未覆写该方法的抽象基类各合并一个具体覆写。
        // 接口 default getInventoryStackLimit 是 setInventorySlotContents 截断（:78-80）与
        // ItemHandler.getSlotLimit（:157-159）的共同来源；只 patch ItemHandler 会广告大于真实写入容量。
        assertTrue(config.contains("NuclearCraftTileInventoryLimitMixin"), "NC late config 应登记基类 mixin")
        assertTrue(mixin.contains("nc.tile.inventory.TileInventory"), "应覆盖 TileInventory 基类")
        assertTrue(mixin.contains("nc.tile.fluid.TileFluidInventory"), "应覆盖 TileFluidInventory 基类")
        assertTrue(mixin.contains("nc.tile.energy.TileEnergyInventory"), "应覆盖 TileEnergyInventory 基类")
        assertTrue(mixin.contains("nc.tile.energyFluid.TileEnergyFluidInventory"), "应覆盖 TileEnergyFluidInventory 基类")
        assertTrue(mixin.contains("public int getInventoryStackLimit()"), "基类必须获得与接口 default 同签名的具体覆写")
        assertTrue(mixin.contains("StackLimitHooks.getCompatibilityStackSize()"), "默认 64 应替换为兼容堆叠上限")
        assertTrue(mixin.contains("@Pseudo"), "基类 mixin 应允许目标类不在编译类路径")
        assertFalse(mixin.contains("getStackSplitSize"), "不得只做 split 尺寸扩张（insertItem 会返回空余量而 ITileInventory 截断）")
        assertFalse(mixin.contains("ModifyReturnValue"), "基类合并方案不需要注入器")
        assertFalse(mixin.contains("@Inject"), "基类合并方案不需要注入器")
        assertFalse(mixin.contains("public int getSlotLimit"), "不得只 patch ItemHandler#getSlotLimit（写入截断仍按 getInventoryStackLimit）")
    }

    @Test
    fun `nuclearCraftLateMixin_shouldBeRegisteredInLoaderAndToggled`() {
        assertTrue(
            StackUpUpLateMixinLoader().getMixinConfigs().any { it.contains("nuclearcraft") },
            "NC late config 必须登记在 loader 模块表",
        )
        assertTrue(
            StackUpUpIds.LATE_NUCLEARCRAFT_MIXIN_CONFIG == "mixins.stackupup.late.nuclearcraft.json",
            "NC 配置名常量必须与资源一致",
        )
    }
}
