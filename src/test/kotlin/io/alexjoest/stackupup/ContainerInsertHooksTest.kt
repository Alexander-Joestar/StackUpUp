package io.alexjoest.stackupup

import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Bootstrap
import net.minecraft.inventory.Container
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Hook-adapted tests use real Container.mergeItemStack and inventory setters; Mixin launch coverage lives in dev probes. */
class ContainerInsertHooksTest {
    private var previousMaxStackSize = 64

    @BeforeEach
    fun enableGenericCapacityForHookTests() {
        previousMaxStackSize = StackUpUpConfig.maxStackSize
        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
    }

    @AfterEach
    fun resetCapacityPolicy() {
        StackUpUpConfig.maxStackSize = previousMaxStackSize
        resetInventoryExpansionForTesting()
    }

    private class MergeContainer : Container() {
        override fun canInteractWith(player: EntityPlayer): Boolean = true

        fun add(slot: Slot): Slot = addSlotToContainer(slot)

        fun merge(stack: ItemStack): Boolean = mergeItemStack(stack, 0, 1, false)
    }

    private class TestMerchant : net.minecraft.entity.IMerchant {
        override fun setCustomer(player: EntityPlayer?) = Unit

        override fun getCustomer(): EntityPlayer? = null

        override fun getRecipes(player: EntityPlayer?): net.minecraft.village.MerchantRecipeList? = null

        override fun setRecipes(recipeList: net.minecraft.village.MerchantRecipeList?) = Unit

        override fun useRecipe(recipe: net.minecraft.village.MerchantRecipe) = Unit

        override fun verifySellingItem(stack: ItemStack) = Unit

        override fun getDisplayName(): net.minecraft.util.text.ITextComponent = net.minecraft.util.text.TextComponentString("test merchant")

        override fun getWorld(): net.minecraft.world.World? = null

        override fun getPos(): net.minecraft.util.math.BlockPos = net.minecraft.util.math.BlockPos.ORIGIN
    }

    private fun prepareInventoryPolicy(maxStackSize: Int, expansionEnabled: Boolean, blacklist: Array<String>) {
        StackUpUpConfig.maxStackSize = maxStackSize
        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(expansionEnabled)
        InventoryStackLimitHook.prepare(maxStackSize, blacklist)
    }

    private fun assertEmptySlotMergeConserves(inventory: net.minecraft.inventory.IInventory, slotIndex: Int, itemId: String, expectedStored: Int) {
        val offeredCount = 129
        val offered = ItemStack(createItem(128, itemId), offeredCount, 0)
        val slot = object : Slot(inventory, slotIndex, 0, 0) {
            override fun getSlotStackLimit(): Int = ContainerInsertHooks.resolveMergeSlotLimit(this, offered, super.getSlotStackLimit())
        }
        val container = MergeContainer()
        container.add(slot)

        assertTrue(inventory.getStackInSlot(slotIndex).isEmpty)
        assertEquals(expectedStored, slot.slotStackLimit)
        assertTrue(container.merge(offered))

        val stored = inventory.getStackInSlot(slotIndex).count
        assertEquals(expectedStored, stored)
        assertEquals(offeredCount - expectedStored, offered.count)
        assertEquals(offeredCount, stored + offered.count, "real empty-slot merge must conserve the offered stack")
    }

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
    fun unknownNative64Inventory_shouldStayCappedWhenEmpty() {
        val item = createItem(10000, "unadapted_empty_item")
        val inventory = createInventory(64)
        val slot = Slot(inventory, 0, 0, 0)
        val stack = ItemStack(item, 1, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 64)

        assertEquals(64, result)
    }

    @Test
    fun unknownNative64Inventory_shouldStayCappedWhenNonEmpty() {
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
    fun mergeSlotLimit_shouldReadNonEmptyItemLimitOnce() {
        var reads = 0
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int {
                reads++
                return 128
            }
        }.setRegistryName(ResourceLocation("stackupup_test", "single_item_limit_read"))
        val stack = ItemStack(item, 1, 0)
        val inventory = createInventory(128)
        val slot = Slot(inventory, 0, 0, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 64)

        assertEquals(128, result)
        assertEquals(1, reads)
    }

    @Test
    fun placementLimit_shouldReadNonEmptyItemLimitOnce() {
        var reads = 0
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int {
                reads++
                return 128
            }
        }.setRegistryName(ResourceLocation("stackupup_test", "single_placement_item_limit_read"))
        val stack = ItemStack(item, 1, 0)
        val inventory = createInventory(128)

        val result = StackLimitHooks.resolvePlacementSlotLimit(stack, inventory, 64, 128)

        assertEquals(128, result)
        assertEquals(1, reads)
    }

    @Test
    fun realContainerSlotMerge_shouldConserveWhenInventoryLimitIs128() {
        val item = createItem(256, "real_container_merge_item")
        val inventory = createInventory(128)
        val offeredCount = 200
        val offered = ItemStack(item, offeredCount, 0)
        val slot = object : Slot(inventory, 0, 0, 0) {
            override fun getSlotStackLimit(): Int = ContainerInsertHooks.resolveMergeSlotLimit(this, offered, super.getSlotStackLimit())
        }
        val container = MergeContainer()
        container.add(slot)

        assertTrue(container.merge(offered))

        val stored = inventory.getStackInSlot(0).count
        val remainder = offered.count
        assertEquals(128, stored, "真实 Container/Slot 写入应受 inventory 上限约束")
        assertEquals(offeredCount, stored + remainder, "真实 Container/Slot 写入必须守恒")
        assertEquals(72, remainder)
    }

    @Test
    fun realContainerMerge_shouldRespectTightestDeclaredInventoryAndItemLimit() {
        assertRealContainerMergeLimit(40, 100, 90, 40, "merge_declared_limit")
        assertRealContainerMergeLimit(100, 50, 90, 50, "merge_inventory_limit")
        assertRealContainerMergeLimit(100, 100, 30, 30, "merge_item_limit")
    }

    private fun assertRealContainerMergeLimit(declaredSlotLimit: Int, inventoryLimit: Int, itemLimit: Int, expectedStored: Int, itemId: String) {
        val offeredCount = 96
        val offered = ItemStack(createItem(itemLimit, itemId), offeredCount, 0)
        val inventory = createInventory(inventoryLimit)
        val slot = object : Slot(inventory, 0, 0, 0) {
            override fun getSlotStackLimit(): Int = ContainerInsertHooks.resolveMergeSlotLimit(this, offered, declaredSlotLimit)
        }
        val container = MergeContainer()
        container.add(slot)

        assertTrue(container.merge(offered))

        val stored = inventory.getStackInSlot(0).count
        val remainder = offered.count
        assertEquals(expectedStored, stored)
        assertEquals(offeredCount - expectedStored, remainder)
        assertEquals(offeredCount, stored + remainder, "real Container/Slot merge must conserve the offered stack")
    }

    @Test
    fun preparedExactInventoryBasic_offDefault64_shouldConserveEmptySlotMerge() {
        prepareInventoryPolicy(64, expansionEnabled = false, blacklist = emptyArray())
        val inventory = InventoryBasic("prepared-off", false, 1)

        assertEquals(InventoryBasic::class.java, inventory.javaClass)
        assertEquals(64, inventory.inventoryStackLimit)
        assertEmptySlotMergeConserves(inventory, 0, "prepared_basic_off", expectedStored = 64)
    }

    @Test
    fun preparedExactInventoryBasic_onDefault64_shouldConserveEmptySlotMerge() {
        prepareInventoryPolicy(64, expansionEnabled = true, blacklist = emptyArray())
        val inventory = InventoryBasic("prepared-on-64", false, 1)

        assertEquals(InventoryBasic::class.java, inventory.javaClass)
        assertEquals(64, inventory.inventoryStackLimit)
        assertEmptySlotMergeConserves(inventory, 0, "prepared_basic_on_64", expectedStored = 64)
    }

    @Test
    fun preparedExactInventoryBasic_onUnrelatedBlacklist_shouldConserveEmptySlotMerge() {
        prepareInventoryPolicy(64, expansionEnabled = true, blacklist = arrayOf("io.alexjoest.stackupup.dev"))
        val inventory = InventoryBasic("prepared-unlisted", false, 1)

        assertEquals(InventoryBasic::class.java, inventory.javaClass)
        assertEquals(64, inventory.inventoryStackLimit)
        assertEquals(false, StackLimitHooks.shouldBypassInventoryStackLimit(inventory))
        assertEmptySlotMergeConserves(inventory, 0, "prepared_basic_unlisted", expectedStored = 64)
    }

    @Test
    fun preparedInventoryBasic_actualGetterAbove64_shouldConserveEmptySlotMerge() {
        prepareInventoryPolicy(256, expansionEnabled = true, blacklist = emptyArray())
        val inventory = object : InventoryBasic("prepared-raised-getter", false, 1) {
            override fun getInventoryStackLimit(): Int = 128
        }

        assertEquals(128, inventory.inventoryStackLimit)
        assertEmptySlotMergeConserves(inventory, 0, "prepared_basic_getter_128", expectedStored = 128)
    }

    @Test
    fun preparedNative64FurnaceHopperAndMerchant_shouldConserveEmptySlotMerges() {
        prepareInventoryPolicy(256, expansionEnabled = true, blacklist = emptyArray())
        assertEmptySlotMergeConserves(net.minecraft.tileentity.TileEntityFurnace(), 0, "prepared_furnace_64", expectedStored = 64)
        assertEmptySlotMergeConserves(net.minecraft.tileentity.TileEntityHopper(), 0, "prepared_hopper_64", expectedStored = 64)
        assertEmptySlotMergeConserves(
            net.minecraft.inventory.InventoryMerchant(null, TestMerchant()),
            0,
            "prepared_merchant_64",
            expectedStored = 64,
        )
    }

    @Test
    fun nonPositiveInventoryLimit_shouldRefuseMergeWithoutConsumingSource() {
        assertMergeRefused(0, "zero_inv_merge_item")
        assertMergeRefused(-1, "negative_inv_merge_item")
    }

    private fun assertMergeRefused(inventoryLimit: Int, itemId: String) {
        val item = createItem(10000, itemId)
        val inventory = createInventory(inventoryLimit)
        val offeredCount = 32
        val offered = ItemStack(item, offeredCount, 0)
        val slot = object : Slot(inventory, 0, 0, 0) {
            override fun getSlotStackLimit(): Int = ContainerInsertHooks.resolveMergeSlotLimit(this, offered, super.getSlotStackLimit())
        }
        val container = MergeContainer()
        container.add(slot)

        assertEquals(0, slot.slotStackLimit)
        container.merge(offered)

        assertTrue(slot.stack.isEmpty)
        assertEquals(offeredCount, offered.count)
        assertEquals(offeredCount, offered.count + slot.stack.count)
    }

    @Test
    fun nonPositiveInventoryLimit_shouldReturnZeroWhenZero() {
        val item = createItem(10000, "zero_inv_item")
        val inventory = createInventory(0)
        val slot = Slot(inventory, 0, 0, 0)
        val stack = ItemStack(item, 1, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 64)

        assertEquals(0, result)
    }

    @Test
    fun nonPositiveInventoryLimit_shouldReturnZeroWhenNegative() {
        val item = createItem(10000, "negative_inv_item")
        val inventory = createInventory(-1)
        val slot = Slot(inventory, 0, 0, 0)
        val stack = ItemStack(item, 1, 0)

        val result = ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, 64)

        assertEquals(0, result)
    }

    @Test
    fun nonPositiveInventoryLimit_shouldNotReadItemLimit() {
        assertItemLimitNotRead(0, "zero_inv_read_item")
        assertItemLimitNotRead(-1, "negative_inv_read_item")
    }

    private fun assertItemLimitNotRead(inventoryLimit: Int, itemId: String) {
        var reads = 0
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int {
                reads++
                return 10000
            }
        }.setRegistryName(ResourceLocation("stackupup_test", itemId))
        val inventory = createInventory(inventoryLimit)
        val slot = Slot(inventory, 0, 0, 0)

        assertEquals(0, ContainerInsertHooks.resolveMergeSlotLimit(slot, ItemStack(item, 1, 0), 64))
        assertEquals(0, reads)
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
