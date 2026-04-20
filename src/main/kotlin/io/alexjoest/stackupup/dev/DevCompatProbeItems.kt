package io.alexjoest.stackupup.dev

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import io.alexjoest.stackupup.StackLimitHooks

object DevCompatProbeItems {
    private val gridExtractProbeItem: Item =
        object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = StackLimitHooks.getCompatibilityStackSize()
        }

    @JvmStatic
    fun createGridExtractProbeStack(count: Int = 1): ItemStack = ItemStack(gridExtractProbeItem, count, 0)
}


