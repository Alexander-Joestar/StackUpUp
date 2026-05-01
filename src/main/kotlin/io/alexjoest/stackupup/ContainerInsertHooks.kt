package io.alexjoest.stackupup

import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack

object ContainerInsertHooks {
    /**
     * `Slot#putStack` 是 void；某些模组会在写入时偷偷把数量截到上限，
     * 但不会把多余部分返还给调用方。
     *
     * 按"写入后槽位实际留下多少"反推 remainder，覆盖以下情况：
     * - 空槽写入后被截断
     * - 空槽写入后物品类型一致但 NBT/数量被改写
     * - 空槽写入后库存直接把传入的 stack 就地变异
     */

    private const val VANILLA_STACK_LIMIT = Constants.VANILLA_STACK_LIMIT

    @JvmStatic
    fun remainderCountAfterEmptyPut(slot: Slot, attemptedStack: ItemStack): Int = remainderCountAfterEmptyPut(slot, attemptedStack, attemptedStack.count)

    @JvmStatic
    fun remainderCountAfterEmptyPut(slot: Slot, attemptedStack: ItemStack, attemptedCount: Int): Int {
        if (attemptedStack.isEmpty || attemptedCount <= 0) {
            return 0
        }

        val storedStack = slot.stack
        if (storedStack.isEmpty) {
            // 槽位完全为空 → 全部未接受
            return attemptedCount
        }

        val acceptedCount = resolveAcceptedCount(storedStack, attemptedStack, attemptedCount)
        return resolveRemainder(storedStack, attemptedStack, attemptedCount, acceptedCount)
    }

    /**
     * 通用余量检测：写入后无论槽位之前是否为空，按实际库存变化反推被吞数量。
     * 适用于 PICKUP、SWAP、mergeItemStack 等所有 putStack 调用点。
     */
    @JvmStatic
    fun remainderAfterPut(slot: Slot, attemptedStack: ItemStack, attemptedCount: Int): Int {
        if (attemptedStack.isEmpty || attemptedCount <= 0) {
            return 0
        }

        // 快速路径：尝试数量不超过当前槽位与库存的真实上限时，不可能有 remainder。
        // 大型整合包中 hopper/管道/AE2 等自动化每 tick 触发大量 mergeItemStack，
        // 这条快速路径避免了后续的 slot.stack 读取和 NBT 比较，同时保留低上限槽位的正确性。
        val slotLimit = slot.slotStackLimit
        val inventoryLimit = slot.inventory.inventoryStackLimit
        val effectiveLimit = minOf(
            VANILLA_STACK_LIMIT,
            if (slotLimit > 0) slotLimit else VANILLA_STACK_LIMIT,
            if (inventoryLimit > 0) inventoryLimit else VANILLA_STACK_LIMIT,
        )
        if (attemptedCount <= effectiveLimit) {
            return 0
        }

        val storedStack = slot.stack
        if (storedStack.isEmpty) {
            return attemptedCount
        }

        val acceptedCount = resolveAcceptedCount(storedStack, attemptedStack, attemptedCount)
        return resolveRemainder(storedStack, attemptedStack, attemptedCount, acceptedCount)
    }

    @JvmStatic
    fun resolveMergeSlotLimit(slot: Slot, stack: ItemStack, declaredSlotLimit: Int): Int {
        val inventoryLimit = slot.inventory.inventoryStackLimit
        if (inventoryLimit <= 0) {
            return resolveContainerMergeSlotLimit(stack, slot.getHasStack(), declaredSlotLimit, VANILLA_STACK_LIMIT)
        }
        return resolveContainerMergeSlotLimit(stack, slot.getHasStack(), declaredSlotLimit, inventoryLimit)
    }

    private fun resolveAcceptedCount(storedStack: ItemStack, attemptedStack: ItemStack, attemptedCount: Int): Int {
        val itemsMatch = ItemStack.areItemsEqual(storedStack, attemptedStack) &&
            ItemStack.areItemStackTagsEqual(storedStack, attemptedStack)

        return if (itemsMatch) {
            // 同类物品：以槽内实际接纳数量为准，上限不超过尝试数量
            minOf(storedStack.count, attemptedCount)
        } else {
            // 不同物品 → 说明槽位被完全替换，未接受任何原尝试物品
            0
        }
    }

    private fun resolveRemainder(storedStack: ItemStack, attemptedStack: ItemStack, attemptedCount: Int, acceptedCount: Int): Int {
        val remainder = (attemptedCount - acceptedCount).coerceAtLeast(0)
        // Some inventories keep/spawn the leftover by mutating the argument to the remainder count.
        if (remainder > 0 && storedStack !== attemptedStack && attemptedStack.count == remainder) {
            return 0
        }
        return remainder
    }

    private fun resolveContainerMergeSlotLimit(stack: ItemStack, slotHasStack: Boolean, declaredSlotLimit: Int, inventoryLimit: Int): Int {
        val effectiveInventoryLimit = if (inventoryLimit > 0) inventoryLimit else VANILLA_STACK_LIMIT
        if (!slotHasStack) {
            return minOf(declaredSlotLimit, effectiveInventoryLimit)
        }
        return StackLimitHooks.resolveDynamicSlotLimit(stack, minOf(declaredSlotLimit, effectiveInventoryLimit))
    }
}
