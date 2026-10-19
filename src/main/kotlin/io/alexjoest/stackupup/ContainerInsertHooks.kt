package io.alexjoest.stackupup

import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack

object ContainerInsertHooks {
    @JvmStatic
    fun resolveMergeSlotLimit(slot: Slot, stack: ItemStack, declaredSlotLimit: Int): Int {
        if (StackLimitHooks.isSpecialContainerInventory(slot.inventory)) {
            return declaredSlotLimit
        }
        val inventoryLimit = StackLimitHooks.resolveInventoryWriteLimit(
            stack,
            slot.inventory,
            slot.inventory.inventoryStackLimit,
        )
        if (inventoryLimit <= 0) return 0

        val dynamic = StackLimitHooks.resolveDynamicSlotLimit(stack, declaredSlotLimit)
        return if (stack.isEmpty) minOf(dynamic, inventoryLimit) else minOf(dynamic, inventoryLimit, stack.maxStackSize)
    }
}
