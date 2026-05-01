package io.alexjoest.stackupup

import net.minecraft.inventory.Container
import net.minecraft.item.ItemStack

object ClientSlotSyncHooks {
    @JvmStatic
    fun restoreContainerSlotStackCounts(container: Container, transmittedStacks: List<ItemStack>) {
        val size = minOf(container.inventorySlots.size, transmittedStacks.size)
        for (slotId in 0 until size) {
            val transmitted = transmittedStacks[slotId]
            restoreContainerSlotStackCount(container, slotId, transmitted, transmitted.count)
        }
    }

    @JvmStatic
    fun restoreContainerSlotStackCount(container: Container, slotId: Int, transmittedStack: ItemStack, transmittedCount: Int) {
        if (slotId < 0 || slotId >= container.inventorySlots.size || transmittedStack.isEmpty || transmittedCount <= 0) {
            return
        }

        val currentStack = container.getSlot(slotId).stack
        if (currentStack.isEmpty) {
            val restored = transmittedStack.copy()
            restored.count = transmittedCount
            container.getSlot(slotId).putStack(restored)
            return
        }

        if (currentStack.count >= transmittedCount || !sameStackForClientSync(currentStack, transmittedStack)) {
            return
        }

        currentStack.count = transmittedCount
    }

    private fun sameStackForClientSync(left: ItemStack, right: ItemStack): Boolean =
        ItemStack.areItemsEqual(left, right) && ItemStack.areItemStackTagsEqual(left, right)
}
