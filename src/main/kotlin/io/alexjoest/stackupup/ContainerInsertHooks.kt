package io.alexjoest.stackupup

import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack

object ContainerInsertHooks {
    @JvmStatic
    fun resolveMergeSlotLimit(slot: Slot, stack: ItemStack, declaredSlotLimit: Int): Int {
        val inventoryLimit = slot.inventory.inventoryStackLimit
        val dynamic = StackLimitHooks.resolveDynamicSlotLimit(stack, declaredSlotLimit)
        return if (inventoryLimit > 0) minOf(dynamic, inventoryLimit) else dynamic
    }
}
