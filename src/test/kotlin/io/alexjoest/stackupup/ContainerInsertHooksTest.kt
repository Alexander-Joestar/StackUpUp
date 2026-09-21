package io.alexjoest.stackupup

import net.minecraft.init.Bootstrap
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ContainerInsertHooksTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUpBootstrap() {
            Bootstrap.register()
        }

        private fun createItem(maxStack: Int, id: String): Item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int = maxStack
        }.setRegistryName(ResourceLocation("stackupup_test", id))

        private fun createInventory(limit: Int): InventoryBasic = object : InventoryBasic("test", false, 1) {
            override fun getInventoryStackLimit(): Int = limit
        }
    }

    @Test
    fun unadaptedInventory_shouldClampLimitTo64WhenEmpty() {
        val item = createItem(10000, "unadapted_empty_item")
        val inventory = createInventory(64)
        val slot = Slot(inventory, 0, 0, 0)
        val stack = ItemStack(item, 1, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 64)

        assertEquals(64, result)
    }

    @Test
    fun unadaptedInventory_shouldClampLimitTo64WhenNonEmpty() {
        val item = createItem(10000, "unadapted_non_empty_item")
        val inventory = createInventory(64)
        val slot = Slot(inventory, 0, 0, 0)
        slot.putStack(ItemStack(item, 10, 0))
        val stack = ItemStack(item, 1, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 64)

        assertEquals(64, result)
    }

    @Test
    fun adaptedInventory_shouldExpandLimitTo10000WhenEmpty() {
        val item = createItem(10000, "adapted_empty_item")
        val inventory = createInventory(10000)
        val slot = Slot(inventory, 0, 0, 0)
        val stack = ItemStack(item, 1, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 64)

        assertEquals(10000, result)
    }

    @Test
    fun adaptedInventory_shouldExpandLimitTo10000WhenNonEmpty() {
        val item = createItem(10000, "adapted_non_empty_item")
        val inventory = createInventory(10000)
        val slot = Slot(inventory, 0, 0, 0)
        slot.putStack(ItemStack(item, 10, 0))
        val stack = ItemStack(item, 1, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 64)

        assertEquals(10000, result)
    }

    @Test
    fun nonPositiveInventoryLimit_shouldReturnDynamicLimitWhenZero() {
        val item = createItem(10000, "zero_inv_item")
        val inventory = createInventory(0)
        val slot = Slot(inventory, 0, 0, 0)
        val stack = ItemStack(item, 1, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 64)

        assertEquals(10000, result)
    }

    @Test
    fun nonPositiveInventoryLimit_shouldReturnDynamicLimitWhenNegative() {
        val item = createItem(10000, "negative_inv_item")
        val inventory = createInventory(-1)
        val slot = Slot(inventory, 0, 0, 0)
        val stack = ItemStack(item, 1, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 64)

        assertEquals(10000, result)
    }

    @Test
    fun nonVanillaDeclaredSlotLimit_shouldNotBeAmplified() {
        val item = createItem(10000, "custom_slot_item")
        val inventory = createInventory(10000)
        val slot = Slot(inventory, 0, 0, 0)
        val stack = ItemStack(item, 1, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 1)

        assertEquals(1, result)
    }

    @Test
    fun inventoryCapacityBetweenVanillaAndDynamic_shouldClampToInventoryCapacity() {
        val item = createItem(10000, "intermediate_inv_item")
        val inventory = createInventory(500)
        val slot = Slot(inventory, 0, 0, 0)
        val stack = ItemStack(item, 1, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 64)

        assertEquals(500, result)
    }

    @Test
    fun emptyItemStack_shouldPreserveDeclaredSlotLimitClamped() {
        val inventory = createInventory(10000)
        val slot = Slot(inventory, 0, 0, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, ItemStack.EMPTY, 64)

        assertEquals(64, result)
    }
}
