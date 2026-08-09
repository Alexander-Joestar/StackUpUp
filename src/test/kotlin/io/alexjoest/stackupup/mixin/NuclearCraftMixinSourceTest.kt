package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector
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
            StackUpUpMixinConnector().modules.any { it.config.contains("nuclearcraft") },
            "NC late config 必须登记在 connector 模块表",
        )
        assertTrue(
            StackUpUpIds.LATE_NUCLEARCRAFT_MIXIN_CONFIG == "mixins.stackupup.late.nuclearcraft.json",
            "NC 配置名常量必须与资源一致",
        )
    }

    @Test
    fun `nuclearCraftDistributorNoDropMixin_shouldSkipCullAndDropWithoutRedirect`() {
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.late.nuclearcraft.json")), Charsets.UTF_8)
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/NuclearCraftDistributorNoDropMixin.java"),
            ),
            Charsets.UTF_8,
        )

        // 源文本断言：裁减（setCount）与掉落（dropOverflow）两条副作用路径必须被注入覆盖。
        assertTrue(config.contains("NuclearCraftDistributorNoDropMixin"), "NC late config 应登记 Distributor NoDrop mixin")
        assertTrue(source.contains("@Pseudo"), "NC 未加载时目标类不在编译类路径，必须 Pseudo 跳过")
        assertTrue(source.contains("nc.multiblock.distributor.Distributor"), "目标为 Distributor 多块结构对象")
        assertTrue(source.contains("cullInventory()Z"), "必须覆盖 cullInventory（防裁减：保留超限存量靠 distributeItems 自然排空）")
        assertTrue(source.contains("dropOverflow(Ljava/util/List;)V"), "必须覆盖 dropOverflow（防掉落，含 onAssimilate 合并调用点）")
        assertTrue(source.contains("setReturnValue(false)"), "cullInventory 应恒返回 false（不触发额外同步）")
        assertTrue(source.contains("require = 0"), "目标缺失时必须静默跳过")
        assertFalse(source.contains("@Redirect"), "不得新增 @Redirect")
        assertFalse(source.contains("@Overwrite"), "不得使用 @Overwrite")

        // 字节码结构检查：编译产物必须真实携带 @Mixin 注解、@Inject 注入器与目标串
        // （注解值以 UTF8 存于常量池，containsAscii 可证），防止源文件与编译产物脱节。
        val mixinClass = Class.forName("io.alexjoest.stackupup.mixin.late.NuclearCraftDistributorNoDropMixin")
        val classBytes = requireNotNull(mixinClass.getResourceAsStream("NuclearCraftDistributorNoDropMixin.class")) {
            "无法读取 NuclearCraftDistributorNoDropMixin.class"
        }.use { it.readBytes() }

        assertTrue(classBytes.containsAscii("Lorg/spongepowered/asm/mixin/Mixin;"), "编译产物应携带 @Mixin 注解")
        assertTrue(classBytes.containsAscii("Lorg/spongepowered/asm/mixin/injection/Inject;"), "编译产物应携带 @Inject 注入器")
        assertTrue(classBytes.containsAscii("cullInventory()Z"), "编译产物应携带 cullInventory 目标")
        assertTrue(classBytes.containsAscii("dropOverflow(Ljava/util/List;)V"), "编译产物应携带 dropOverflow 目标")
    }

    private fun ByteArray.containsAscii(value: String): Boolean {
        if (isEmpty()) {
            return false
        }

        val target = value.encodeToByteArray()
        val lastIndex = size - target.size
        if (lastIndex < 0) {
            return false
        }

        for (index in 0..lastIndex) {
            var matches = true
            for (offset in target.indices) {
                if (this[index + offset] != target[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                return true
            }
        }
        return false
    }
}
