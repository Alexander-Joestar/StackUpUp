package io.alexjoest.stackupup.core

import net.minecraft.item.ItemStack
import net.minecraftforge.items.IItemHandler

class TestUnsafeItemHandler : IItemHandler {
    override fun getSlots(): Int = 1

    override fun getStackInSlot(slot: Int): ItemStack = ItemStack.EMPTY

    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack = ItemStack.EMPTY

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = ItemStack.EMPTY

    override fun getSlotLimit(slot: Int): Int = 64
}
