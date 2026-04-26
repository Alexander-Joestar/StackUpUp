package io.alexjoest.stackupup

import net.minecraft.init.Bootstrap
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContainerInsertHooksTest {
    @BeforeEach
    fun setUpBootstrap() {
        Bootstrap.register()
    }

    @Test
    fun `remainder reflects clamped empty-slot writes`() {
        val inventory = object : InventoryBasic("test", false, 1) {
            override fun setInventorySlotContents(index: Int, stack: ItemStack) {
                val stored = stack.copy()
                if (!stored.isEmpty && stored.count > 64) {
                    stored.setCount(64)
                }
                super.setInventorySlotContents(index, stored)
            }
        }
        val slot = Slot(inventory, 0, 0, 0)
        val attempted = ItemStack(Item(), 200)

        slot.putStack(attempted.copy())

        assertEquals(136, ContainerInsertHooks.remainderCountAfterEmptyPut(slot, attempted))
    }

    @Test
    fun `remainder is zero when slot accepts full stack`() {
        val inventory = InventoryBasic("test", false, 1)
        val slot = Slot(inventory, 0, 0, 0)
        val attempted = ItemStack(Item(), 48)

        slot.putStack(attempted.copy())

        assertEquals(0, ContainerInsertHooks.remainderCountAfterEmptyPut(slot, attempted))
    }

    @Test
    fun `remainder keeps the whole attempted stack when slot stays empty`() {
        val slot = Slot(InventoryBasic("test", false, 1), 0, 0, 0)
        val attempted = ItemStack(Item(), 48)

        assertEquals(48, ContainerInsertHooks.remainderCountAfterEmptyPut(slot, attempted))
    }
}
