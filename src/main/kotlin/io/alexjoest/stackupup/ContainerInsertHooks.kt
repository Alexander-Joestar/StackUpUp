package io.alexjoest.stackupup

import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack

object ContainerInsertHooks {
    @JvmStatic
    fun resolveMergeSlotLimit(slot: Slot, stack: ItemStack, declaredSlotLimit: Int): Int {
        val inventoryLimit = slot.inventory.inventoryStackLimit
        if (inventoryLimit <= 0) return 0

        val dynamic = StackLimitHooks.resolveDynamicSlotLimit(stack, declaredSlotLimit)
        return minOf(dynamic, inventoryLimit)
    }
}
