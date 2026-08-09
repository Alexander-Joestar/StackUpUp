package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class EnderIOMixinSourceTest {
    @Test
    fun `enderIoMachineMixin_shouldTargetNoArgInventoryLimitDescriptor`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/EnderIOMachineInventoryLimitMixin.java"),
            ),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("getInventoryStackLimit()I"))
    }

    @Test
    fun `enderIoSlottedMixin_shouldTargetSlotAwareInventoryLimitDescriptor`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/EnderIOSlottedInventoryLimitMixin.java"),
            ),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("getInventoryStackLimit(I)I"))
    }

    @Test
    fun `enderIoInventorySlotMixin_shouldGuard64AndPreserveExplicitLimitsAndCrafterOverride`() {
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.late.enderio.json")), Charsets.UTF_8)
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/EnderIOInventorySlotLimitMixin.java")),
            Charsets.UTF_8,
        )

        assertTrue(config.contains("EnderIOInventorySlotLimitMixin"), "EnderIO late config 应登记 InventorySlot mixin")
        assertTrue(source.contains("@Pseudo"), "应允许目标类不在编译类路径（旧 EnderIO 1.5-1.12 无 EnderCore InventorySlot）")
        assertTrue(source.contains("com.enderio.core.common.inventory.InventorySlot"), "应覆盖 EnderCore InventorySlot")
        assertTrue(source.contains("getMaxStackSize()I"), "应覆盖带 descriptor 的 getMaxStackSize（区分重载）")
        assertTrue(source.contains("ModifyReturnValue"), "应使用 ModifyReturnValue 收口返回值")
        assertTrue(source.contains("require = 0"), "目标缺失时必须静默跳过")
        assertTrue(source.contains("original == VANILLA_STACK_LIMIT"), "只替换默认 64，显式 limit 保持")
        assertTrue(source.contains("StackLimitHooks.getCompatibilityStackSize()"), "默认 64 应替换为兼容堆叠上限")
        assertFalse(source.contains("@Overwrite"), "不得使用 @Overwrite")
        assertFalse(source.contains("@Redirect"), "不得新增 @Redirect")
        assertFalse(source.contains("crazypants.enderio.machines.machine.crafter"), "不得把 TileCrafter 缓冲槽作为 mixin 目标（其匿名覆写 bufferStacks ? 64 : 1 语义保留）")
    }

    @Test
    fun `enderIoNoDropMixin_shouldSuppressShrinkAndSpawnWithoutRedirect`() {
        val config = String(Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.late.enderio.json")), Charsets.UTF_8)
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/EnderIOInventoryNoDropMixin.java"),
            ),
            Charsets.UTF_8,
        )

        // 源文本断言：掉落与吞并两条副作用路径必须被注入覆盖，且符合项目注入器红线。
        assertTrue(config.contains("EnderIOInventoryNoDropMixin"), "EnderIO late config 应登记 NoDrop mixin")
        assertTrue(source.contains("@Pseudo"), "EnderIO 未加载时目标类不在编译类路径，必须 Pseudo 跳过")
        assertTrue(source.contains("crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity"), "目标为 legacy 机器公共基类")
        assertTrue(source.contains("setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V"), "注入方法必须带完整 descriptor")
        assertTrue(source.contains("Lnet/minecraft/item/ItemStack;shrink(I)V"), "必须覆盖 contents.shrink（防吞：剩余物留在调用方栈）")
        assertTrue(
            source.contains(
                "Lnet/minecraft/block/Block;spawnAsEntity(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
            ),
            "必须覆盖 Block.spawnAsEntity（防掉落）",
        )
        assertTrue(source.contains("WrapOperation"), "抑制原调用应使用 @WrapOperation（项目禁止新增 @Redirect）")
        assertTrue(source.contains("require = 0"), "目标缺失时必须静默跳过")
        assertFalse(source.contains("@Redirect"), "不得新增 @Redirect")
        assertFalse(source.contains("@Overwrite"), "不得使用 @Overwrite")
        assertFalse(
            source.contains("target = \"Lnet/minecraft/item/ItemStack;setCount(I)V\""),
            "不得注入 setCount（槽内夹取是写入面既有行为，保留；javadoc 提及不算注入）",
        )

        // 字节码结构检查：编译产物必须真实携带 @Mixin 注解、@WrapOperation 注入器与两个 @At 目标串
        // （注解值以 UTF8 存于常量池，containsAscii 可证），防止源文件与编译产物脱节。
        val mixinClass = Class.forName("io.alexjoest.stackupup.mixin.late.EnderIOInventoryNoDropMixin")
        val classBytes = requireNotNull(mixinClass.getResourceAsStream("EnderIOInventoryNoDropMixin.class")) {
            "无法读取 EnderIOInventoryNoDropMixin.class"
        }.use { it.readBytes() }

        assertTrue(classBytes.containsAscii("Lorg/spongepowered/asm/mixin/Mixin;"), "编译产物应携带 @Mixin 注解")
        assertTrue(classBytes.containsAscii("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"), "编译产物应携带 @WrapOperation 注入器")
        assertTrue(classBytes.containsAscii("spawnAsEntity"), "编译产物应携带 spawnAsEntity 目标")
        assertTrue(classBytes.containsAscii("shrink(I)V"), "编译产物应携带 shrink 目标")
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
