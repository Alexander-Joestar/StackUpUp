package io.alexjoest.stackupup

import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack

object ContainerInsertHooks {
    /**
     * `Slot#putStack` 是 void；某些模组会在写入时偷偷把数量截到 64，
     * 但不会把多余部分返还给调用方。这里按“空槽位写入后实际留下了多少”反推 remainder。
     */
    @JvmStatic
    fun remainderCountAfterEmptyPut(slot: Slot, attemptedStack: ItemStack): Int {
        if (attemptedStack.isEmpty) {
            return 0
        }

        val storedStack = slot.stack
        if (storedStack.isEmpty) {
            return attemptedStack.count
        }

        val acceptedCount = if (
            ItemStack.areItemsEqual(storedStack, attemptedStack) &&
            ItemStack.areItemStackTagsEqual(storedStack, attemptedStack)
        ) {
            minOf(storedStack.count, attemptedStack.count)
        } else {
            0
        }

        return attemptedStack.count - acceptedCount
    }
}
