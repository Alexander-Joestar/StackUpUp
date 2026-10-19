package io.alexjoest.stackupup

import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack

object ContainerInsertHooks {
    @JvmStatic
    fun resolveMergeSlotLimit(slot: Slot, stack: ItemStack, declaredSlotLimit: Int): Int {
        if (StackLimitHooks.isSpecialContainerInventory(slot.inventory)) {
            return declaredSlotLimit
        }
        val rawInventoryLimit = slot.inventory.inventoryStackLimit
        if (rawInventoryLimit <= 0) return 0
        if (stack.isEmpty) {
            val inventoryLimit = StackLimitHooks.resolveInventoryWriteLimit(
                stack,
                slot.inventory,
                rawInventoryLimit,
            )
            if (inventoryLimit <= 0) return 0
            return minOf(declaredSlotLimit, inventoryLimit)
        }

        val itemLimit = stack.maxStackSize
        val inventoryLimit = StackLimitHooks.resolveInventoryWriteLimit(
            stack,
            slot.inventory,
            rawInventoryLimit,
            itemLimit,
        )
        if (inventoryLimit <= 0) return 0

        return if (declaredSlotLimit == Constants.VANILLA_STACK_LIMIT) {
            inventoryLimit
        } else {
            minOf(declaredSlotLimit, inventoryLimit)
        }
    }
}
