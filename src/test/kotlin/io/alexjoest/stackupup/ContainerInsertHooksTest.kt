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

        fun merge(stack: ItemStack, endIndex: Int = 1): Boolean = mergeItemStack(stack, 0, endIndex, false)
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

        private fun createInventory(limit: Int, size: Int = 1): InventoryBasic = object : InventoryBasic("test", false, size) {
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
    fun singleMergeOverEmptyChestSlots_shouldFillExactlyOneSlot() {
        prepareInventoryPolicy(1024, expansionEnabled = true, blacklist = emptyArray())
        val offeredCount = 128
        val offered = ItemStack(createItem(1024, "chest_slot_single_fill_item"), offeredCount, 0)
        val inventory = createInventory(1024, 27)
        val container = MergeContainer()
        repeat(27) { index ->
            container.add(
                object : Slot(inventory, index, 0, 0) {
                    override fun getSlotStackLimit(): Int = ContainerInsertHooks.resolveMergeSlotLimit(this, offered, super.getSlotStackLimit())
                },
            )
        }

        assertTrue(container.merge(offered, 27))

        assertEquals(offeredCount, inventory.getStackInSlot(0).count)
        (1 until 27).forEach { index ->
            assertTrue(
                inventory.getStackInSlot(index).isEmpty,
                "one mergeItemStack call must not spill into slot $index",
            )
        }
        assertEquals(0, offered.count)
        val touchedSlots = (0 until 27).count { !inventory.getStackInSlot(it).isEmpty }
        assertEquals(1, touchedSlots, "one mergeItemStack call over 27 empty slots must touch exactly one slot")
    }

    @Test
    fun expandedDoubleChest_shouldResolveSameMergeLimitAsExpandedSingleChest() {
        prepareInventoryPolicy(1024, expansionEnabled = true, blacklist = emptyArray())
        val offered = ItemStack(createItem(1024, "chest_parity_on_item"), 128, 0)
        val single = chestWithGetterLimit(1024)
        val double = net.minecraft.inventory.InventoryLargeChest(
            "stackupup_test_double_on",
            chestWithGetterLimit(1024),
            chestWithGetterLimit(1024),
        )

        assertEquals(1024, single.inventoryStackLimit)
        assertEquals(1024, double.inventoryStackLimit)
        // 扩容开启时容量 lane 把箱体 getter 改写到配置上限；测试用同值 getter 覆写模拟该改写，
        // 双箱只转发上半箱，因此两半都要抬高才等价于生产状态。
        val singleLimit = StackLimitHooks.resolveInventoryWriteLimit(offered, single, single.inventoryStackLimit)
        val doubleLimit = StackLimitHooks.resolveInventoryWriteLimit(offered, double, double.inventoryStackLimit)
        assertEquals(1024, singleLimit)
        assertEquals(singleLimit, doubleLimit, "an expanded double chest must resolve the same write limit as the single chest it wraps")

        // ContainerMixin 走的正是这条路径；双箱 54 槽里只有上半箱首槽被写入。
        assertEquals(1024, ContainerInsertHooks.resolveMergeSlotLimit(Slot(single, 0, 0, 0), offered, single.inventoryStackLimit))
        assertEquals(
            1024,
            ContainerInsertHooks.resolveMergeSlotLimit(Slot(double, 0, 0, 0), offered, double.inventoryStackLimit),
        )
        assertEquals(128, mergeIntoEmptyChestSlots(single, "chest_parity_on_item"))
        assertEquals(128, mergeIntoEmptyChestSlots(double, "chest_parity_on_item"))
    }

    @Test
    fun disabledExpansion_shouldKeepSingleChestExpandedWhileDoubleChestStaysAt64() {
        prepareInventoryPolicy(1024, expansionEnabled = false, blacklist = emptyArray())
        val offered = ItemStack(createItem(1024, "chest_parity_off_item"), 128, 0)
        val single = net.minecraft.tileentity.TileEntityChest()
        val double = net.minecraft.inventory.InventoryLargeChest(
            "stackupup_test_double_off",
            net.minecraft.tileentity.TileEntityChest(),
            net.minecraft.tileentity.TileEntityChest(),
        )

        assertEquals(64, single.inventoryStackLimit)
        assertEquals(64, double.inventoryStackLimit)

        // 记录 M109 已证实的非对称现状，不在本任务修复：CDR §T11/:370、§T13/:390 要求
        // InventoryLargeChest 在两半箱来源不一致时保持不 patch，且扩容关闭时它不在
        // native-64 写入能力表内，因此回落到 64；单箱仍按配置上限写入。
        assertEquals(1024, StackLimitHooks.resolveInventoryWriteLimit(offered, single, single.inventoryStackLimit))
        assertEquals(64, StackLimitHooks.resolveInventoryWriteLimit(offered, double, double.inventoryStackLimit))
        assertEquals(1024, ContainerInsertHooks.resolveMergeSlotLimit(Slot(single, 0, 0, 0), offered, single.inventoryStackLimit))
        assertEquals(64, ContainerInsertHooks.resolveMergeSlotLimit(Slot(double, 0, 0, 0), offered, double.inventoryStackLimit))
    }

    @Test
    fun quickMoveFromPlayerIntoExpandedChest_shouldFillExactlyOneSlot() {
        prepareInventoryPolicy(1024, expansionEnabled = true, blacklist = emptyArray())
        val player = testPlayer()
        val chestInventory = createInventory(1024, 27)
        val container = net.minecraft.inventory.ContainerChest(player.inventory, chestInventory, player)
        assertEquals(9, container.inventorySlots[27].slotIndex, "槽位 27 是玩家背包第一个主槽位")
        val offeredCount = 128
        player.inventory.mainInventory[9] = ItemStack(createItem(1024, "quick_move_expanded_item"), offeredCount, 0)

        container.slotClick(27, 0, net.minecraft.inventory.ClickType.QUICK_MOVE, player)

        assertEquals(offeredCount, chestInventory.getStackInSlot(0).count)
        (1 until 27).forEach { index ->
            assertTrue(chestInventory.getStackInSlot(index).isEmpty, "一次 QUICK_MOVE 必须一次搬完，不得溢出到槽 $index")
        }
        assertTrue(player.inventory.mainInventory[9].isEmpty)
        val touchedSlots = (0 until 27).count { !chestInventory.getStackInSlot(it).isEmpty }
        assertEquals(1, touchedSlots, "一次 QUICK_MOVE 只应占用一个箱体槽位")
    }

    @Test
    fun quickMoveIntoSixtyFourCappedChest_shouldRepeatInWhole64Blocks() {
        prepareInventoryPolicy(1024, expansionEnabled = true, blacklist = emptyArray())
        val player = testPlayer()
        val chestInventory = createInventory(64, 27)
        val container = net.minecraft.inventory.ContainerChest(player.inventory, chestInventory, player)
        val offeredCount = 128
        player.inventory.mainInventory[9] = ItemStack(createItem(1024, "quick_move_capped_item"), offeredCount, 0)

        // 未加载 Mixin 时 mergeItemStack 读槽位声明上限；目标只声明 64 时，Container.slotClick
        // 的 QUICK_MOVE 循环会重复 transferStackInSlot，一次 shift 就摊成 n 个 64 —— 即被报告的现象。
        container.slotClick(27, 0, net.minecraft.inventory.ClickType.QUICK_MOVE, player)

        assertEquals(64, chestInventory.getStackInSlot(0).count)
        assertEquals(64, chestInventory.getStackInSlot(1).count)
        (2 until 27).forEach { index ->
            assertTrue(chestInventory.getStackInSlot(index).isEmpty, "128 分成两个 64 后不得继续溢出到槽 $index")
        }
        assertTrue(player.inventory.mainInventory[9].isEmpty)
        val touchedSlots = (0 until 27).count { !chestInventory.getStackInSlot(it).isEmpty }
        assertEquals(2, touchedSlots, "64 上限目标会把一次 QUICK_MOVE 摊成两个槽位")
    }

    private fun chestWithGetterLimit(limit: Int): net.minecraft.tileentity.TileEntityChest = object : net.minecraft.tileentity.TileEntityChest() {
        override fun getInventoryStackLimit(): Int = limit
    }

    private fun mergeIntoEmptyChestSlots(inventory: net.minecraft.inventory.IInventory, itemId: String): Int {
        val offeredCount = 128
        val offered = ItemStack(createItem(1024, itemId), offeredCount, 0)
        val container = MergeContainer()
        repeat(inventory.sizeInventory) { index ->
            container.add(
                object : Slot(inventory, index, 0, 0) {
                    override fun getSlotStackLimit(): Int = ContainerInsertHooks.resolveMergeSlotLimit(this, offered, super.getSlotStackLimit())
                },
            )
        }

        assertTrue(container.merge(offered, inventory.sizeInventory))
        assertEquals(offeredCount, inventory.getStackInSlot(0).count)
        assertTrue(offered.isEmpty)
        return inventory.getStackInSlot(0).count
    }

    private fun testPlayer(): EntityPlayer = object : EntityPlayer(
        QuickMoveWorld(),
        com.mojang.authlib.GameProfile(java.util.UUID(0L, 1L), "quick_move"),
    ) {
        override fun isSpectator(): Boolean = false

        override fun isCreative(): Boolean = false
    }

    private class QuickMoveWorld :
        net.minecraft.world.World(
            null,
            net.minecraft.world.storage.WorldInfo(net.minecraft.nbt.NBTTagCompound()),
            net.minecraft.world.WorldProviderSurface(),
            net.minecraft.profiler.Profiler(),
            false,
        ) {
        init {
            provider.setWorld(this)
        }

        override fun createChunkProvider(): net.minecraft.world.chunk.IChunkProvider? = null

        override fun isChunkLoaded(x: Int, z: Int, allowEmpty: Boolean): Boolean = false
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
