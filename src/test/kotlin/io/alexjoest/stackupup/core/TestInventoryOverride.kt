package io.alexjoest.stackupup.core

import net.minecraft.inventory.InventoryBasic

internal class TestInventoryOverride : InventoryBasic("stackupup-test", false, 1) {
    override fun getInventoryStackLimit(): Int = 64
}
