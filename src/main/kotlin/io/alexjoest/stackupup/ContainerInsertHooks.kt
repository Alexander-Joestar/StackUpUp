package io.alexjoest.stackupup

import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack

object ContainerInsertHooks {
    private const val VANILLA_STACK_LIMIT = Constants.VANILLA_STACK_LIMIT

    @JvmStatic
    fun remainderCountAfterEmptyPut(slot: Slot, attemptedStack: ItemStack): Int = remainderCountAfterEmptyPut(slot, attemptedStack, attemptedStack.count)

    @JvmStatic
    fun remainderCountAfterEmptyPut(slot: Slot, attemptedStack: ItemStack, attemptedCount: Int): Int {
        if (attemptedStack.isEmpty || attemptedCount <= 0) return 0
        val storedStack = slot.stack
        if (storedStack.isEmpty) return attemptedCount
        val itemsMatch = ItemStack.areItemsEqual(storedStack, attemptedStack) &&
            ItemStack.areItemStackTagsEqual(storedStack, attemptedStack)
        val acceptedCount = if (itemsMatch) minOf(storedStack.count, attemptedCount) else 0
        val remainder = (attemptedCount - acceptedCount).coerceAtLeast(0)
        if (remainder > 0 && storedStack !== attemptedStack && attemptedStack.count == remainder) return 0
        return remainder
    }

    @JvmStatic
    fun remainderAfterPut(slot: Slot, attemptedStack: ItemStack, attemptedCount: Int): Int {
        if (attemptedStack.isEmpty || attemptedCount <= 0) return 0
        val slotLimit = slot.slotStackLimit
        val inventoryLimit = slot.inventory.inventoryStackLimit
        val effectiveLimit = minOf(
            VANILLA_STACK_LIMIT, if (slotLimit > 0) slotLimit else VANILLA_STACK_LIMIT,
            if (inventoryLimit > 0) inventoryLimit else VANILLA_STACK_LIMIT,
        )
        if (attemptedCount <= effectiveLimit) return 0
        val storedStack = slot.stack
        if (storedStack.isEmpty) return attemptedCount
        val itemsMatch = ItemStack.areItemsEqual(storedStack, attemptedStack) &&
            ItemStack.areItemStackTagsEqual(storedStack, attemptedStack)
        val acceptedCount = if (itemsMatch) minOf(storedStack.count, attemptedCount) else 0
        val remainder = (attemptedCount - acceptedCount).coerceAtLeast(0)
        if (remainder > 0 && storedStack !== attemptedStack && attemptedStack.count == remainder) return 0
        return remainder
    }

    @JvmStatic
    fun resolveMergeSlotLimit(slot: Slot, stack: ItemStack, declaredSlotLimit: Int): Int {
        val inventoryLimit = slot.inventory.inventoryStackLimit
        val effectiveInventoryLimit = if (inventoryLimit > 0) inventoryLimit else VANILLA_STACK_LIMIT
        val effective = minOf(declaredSlotLimit, effectiveInventoryLimit)
        if (!slot.getHasStack()) return effective
        return StackLimitHooks.resolveDynamicSlotLimit(stack, effective)
    }
}
