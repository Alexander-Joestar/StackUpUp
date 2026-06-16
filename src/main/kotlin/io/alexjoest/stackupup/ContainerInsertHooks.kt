package io.alexjoest.stackupup

import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack

object ContainerInsertHooks {
    @JvmStatic
    fun resolveMergeSlotLimit(slot: Slot, stack: ItemStack, declaredSlotLimit: Int): Int {
        val inventoryLimit = slot.inventory.inventoryStackLimit
        val effective = minOf(declaredSlotLimit, if (inventoryLimit > 0) inventoryLimit else Constants.VANILLA_STACK_LIMIT)
        if (!slot.hasStack) return effective
        return StackLimitHooks.resolveDynamicSlotLimit(stack, effective)
    }
}
