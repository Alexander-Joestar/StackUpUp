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
}
