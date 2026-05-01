package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackLimitHooks
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

object DevCompatProbeItems {
    private val gridExtractProbeItem: Item =
        object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = StackLimitHooks.getCompatibilityStackSize()
        }

    @JvmStatic
    fun createGridExtractProbeStack(count: Int = 1): ItemStack = ItemStack(gridExtractProbeItem, count, 0)
}
