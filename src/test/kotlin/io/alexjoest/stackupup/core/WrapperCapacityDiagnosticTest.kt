package io.alexjoest.stackupup.core

import net.minecraft.init.Bootstrap
import net.minecraft.inventory.InventoryBasic
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.wrapper.InvWrapper
import net.minecraftforge.items.wrapper.SidedInvWrapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * 诊断用：确认 Forge 转发型 wrapper 的 getSlotLimit 与真实写入容量是否一致。
 *
 * 动机：ForgeItemHandlerLimitMixin 对 InvWrapper / SidedInvWrapper 在 original == 64 时
 * 提升对外上限；Ae2ItemHandlerInsertLimiter 又把这两个类列为 trusted 直通。
 * 若 wrapper 背后的 IInventory 真实只接受 64，这两条策略叠加就是吞物品链。
 * 本测试测量未打 mixin 时的原始行为，作为重构前的事实基线。
 */
class WrapperCapacityDiagnosticTest {
    /** InvWrapper.getSlotLimit 是否直接转发背后 IInventory 的真实上限。 */
    @Test
    fun `invWrapper_slotLimit_reflectsBackingInventoryLimit`() {
        val wrapper = InvWrapper(LimitedInventory(64))
        assertEquals(64, wrapper.getSlotLimit(0), "InvWrapper 应转发背后库存的真实上限")
    }

    /** 关键诊断：向只接受 64 的库存插入 150，wrapper 报告的 remainder 是否诚实。 */
    @Test
    fun `invWrapper_insertOversized_remainderMustAccountForTruncation`() {
        val inventory = LimitedInventory(64)
        val wrapper = InvWrapper(inventory)

        val remainder = wrapper.insertItem(0, stack(150), false)

        val stored = inventory.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        // 守恒：真实写入 + 诚实退回 == 投入总量。不成立即为吞物品。
        assertEquals(
            150,
            stored + remainderCount,
            "投入 150，实际写入 $stored，退回 $remainderCount —— 差额即被吞掉的数量",
        )
    }

    /** SidedInvWrapper 走的是另一套静态插入逻辑，单独确认。 */
    @Test
    fun `sidedInvWrapper_insertOversized_remainderMustAccountForTruncation`() {
        val inventory = LimitedSidedInventory(64)
        val wrapper = SidedInvWrapper(inventory, net.minecraft.util.EnumFacing.NORTH)

        val remainder = wrapper.insertItem(0, stack(150), false)

        val stored = inventory.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        assertEquals(
            150,
            stored + remainderCount,
            "投入 150，实际写入 $stored，退回 $remainderCount",
        )
    }

    /** 端到端：AE2 限流器把 InvWrapper 当 trusted 直通后，守恒是否仍成立。 */
    @Test
    fun `ae2Limiter_trustedInvWrapper_overUnexpandedInventory_conservesItems`() {
        val inventory = LimitedInventory(64)
        val wrapper = InvWrapper(inventory)

        val remainder = Ae2ItemHandlerInsertLimiter.insertCapped(wrapper, 0, stack(150), false)

        val stored = inventory.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        assertEquals(
            150,
            stored + remainderCount,
            "经 trusted 直通后：写入 $stored，退回 $remainderCount",
        )
    }

    private class LimitedInventory(private val stackLimit: Int) :
        InventoryBasic("stackupup-diagnostic", false, 1) {
        override fun getInventoryStackLimit(): Int = stackLimit
    }

    private class LimitedSidedInventory(private val stackLimit: Int) :
        InventoryBasic("stackupup-diagnostic-sided", false, 1),
        net.minecraft.inventory.ISidedInventory {
        override fun getInventoryStackLimit(): Int = stackLimit

        override fun getSlotsForFace(side: net.minecraft.util.EnumFacing): IntArray = intArrayOf(0)

        override fun canInsertItem(index: Int, stack: ItemStack, direction: net.minecraft.util.EnumFacing): Boolean = true

        override fun canExtractItem(index: Int, stack: ItemStack, direction: net.minecraft.util.EnumFacing): Boolean = true
    }

    companion object {
        private lateinit var testItem: Item

        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            Bootstrap.register()
            testItem = Item()
                .setMaxStackSize(1024)
                .setRegistryName(ResourceLocation("stackupup_test", "diagnostic_item"))
        }

        private fun stack(count: Int): ItemStack = ItemStack(testItem, count)
    }
}
