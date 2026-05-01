package io.alexjoest.stackupup

import net.minecraft.init.Bootstrap
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
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
    fun `remainder uses original attempted count when inventory mutates passed stack`() {
        val inventory = object : InventoryBasic("test", false, 1) {
            override fun setInventorySlotContents(index: Int, stack: ItemStack) {
                if (!stack.isEmpty && stack.count > 64) {
                    stack.setCount(64)
                }
                super.setInventorySlotContents(index, stack)
            }
        }
        val slot = Slot(inventory, 0, 0, 0)
        val attempted = ItemStack(Item(), 200)
        val originalCount = attempted.count

        slot.putStack(attempted)

        assertEquals(136, ContainerInsertHooks.remainderCountAfterEmptyPut(slot, attempted, originalCount))
    }

    @Test
    fun `remainder is zero when inventory leaves overflow in argument stack`() {
        val inventory = object : InventoryBasic("test", false, 1) {
            override fun setInventorySlotContents(index: Int, stack: ItemStack) {
                val stored = stack.copy()
                stored.setCount(64)
                super.setInventorySlotContents(index, stored)
                stack.setCount(64)
            }
        }
        val slot = Slot(inventory, 0, 0, 0)
        val attempted = ItemStack(Item(), 128)
        val originalCount = attempted.count

        slot.putStack(attempted)

        assertEquals(0, ContainerInsertHooks.remainderAfterPut(slot, attempted, originalCount))
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

    @Test
    fun `remainder detects partial acceptance when slot copies stack data`() {
        val inventory = object : InventoryBasic("test", false, 1) {
            override fun setInventorySlotContents(index: Int, stack: ItemStack) {
                val stored = stack.copy()
                if (!stored.isEmpty && stored.count > 16) {
                    stored.setCount(16)
                }
                super.setInventorySlotContents(index, stored)
            }
        }
        val slot = Slot(inventory, 0, 0, 0)
        val attempted = ItemStack(Item(), 48)

        slot.putStack(attempted.copy())

        assertEquals(32, ContainerInsertHooks.remainderCountAfterEmptyPut(slot, attempted))
    }

    @Test
    fun `remainder still detects small slot limits below vanilla`() {
        val inventory = object : InventoryBasic("test", false, 1) {
            override fun getInventoryStackLimit(): Int = 1
        }
        val slot = Slot(inventory, 0, 0, 0)
        val attempted = ItemStack(Item(), 16)

        slot.putStack(attempted.copy())

        assertEquals(15, ContainerInsertHooks.remainderAfterPut(slot, attempted, attempted.count))
    }

    @Test
    fun `remainder short circuits when count is under effective limit`() {
        val inventory = InventoryBasic("test", false, 1)
        val slot = Slot(inventory, 0, 0, 0)
        val attempted = ItemStack(Item(), 16)

        slot.putStack(attempted.copy())

        assertEquals(0, ContainerInsertHooks.remainderAfterPut(slot, attempted, attempted.count))
    }

    @Test
    fun `empty merge slot limit does not exceed inventory capacity`() {
        val inventory = object : InventoryBasic("test", false, 1) {
            override fun getInventoryStackLimit(): Int = 64
        }
        val slot = Slot(inventory, 0, 0, 0)
        val attempted = ItemStack(Item(), 128)

        assertEquals(64, ContainerInsertHooks.resolveMergeSlotLimit(slot, attempted, 128))
    }
}
