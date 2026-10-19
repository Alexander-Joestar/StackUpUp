package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.ContainerInsertHooks
import io.alexjoest.stackupup.StackLimitHooks
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.init.Blocks
import net.minecraft.init.Bootstrap
import net.minecraft.inventory.Container
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.wrapper.InvWrapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Hook-adapted vanilla fixtures exercise real writes; this is not a Mixin launch test. */
class CoreCapacityOffBehaviorTest {
    private var previousMax = 64

    private companion object {
        val ENCHANTING_LAPIS_ITEM: Item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int =
                if (StackLimitHooks.shouldBypassDynamicItemRules()) 64 else StackLimitHooks.applyDynamicStackLimit(stack, 64)
        }.setRegistryName(ResourceLocation("stackupup_test", "core_off_enchanting_lapis"))
    }

    @BeforeEach
    fun prepareOffPolicy() {
        Bootstrap.register()
        previousMax = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 256
        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(false)
        InventoryStackLimitHook.prepare(256, null)
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(1L, listOf(io.alexjoest.stackupup.rules.compile.RuleCompiler.compileLine("size >= 1 -> 128", 1))),
        )
    }

    @AfterEach
    fun restore() {
        StackUpUpConfig.maxStackSize = previousMax
        RuleRuntime.replaceSnapshot(RuleSnapshot(0L, emptyList()))
        resetInventoryExpansionForTesting()
    }

    private fun item(): Item = object : Item() {
        override fun getItemStackLimit(stack: ItemStack): Int =
            if (StackLimitHooks.shouldBypassDynamicItemRules()) 64 else StackLimitHooks.applyDynamicStackLimit(stack, 64)
    }.setRegistryName(ResourceLocation("stackupup_test", "core_off_item"))

    private fun chest(): TileEntityChest {
        val vanillaReceiver = TileEntityChest()
        return object : TileEntityChest() {
            override fun getInventoryStackLimit(): Int = StackLimitHooks.getCoreInventoryStackSize(vanillaReceiver, super.getInventoryStackLimit())
        }
    }

    @Test
    fun offPolicy_coreReceiverExpandsButUnknownSubclassDoesNot() {
        assertEquals(256, StackLimitHooks.getCoreInventoryStackSize(TileEntityChest(), 64))
        assertEquals(64, StackLimitHooks.getCoreInventoryStackSize(object : TileEntityChest() {}, 64))
        assertEquals(16, StackLimitHooks.getCoreInventoryStackSize(TileEntityChest(), 16))
        assertEquals(1, StackLimitHooks.getCoreInventoryStackSize(TileEntityChest(), 1))
    }

    @Test
    fun offPolicy_playerPickupAndMergingStoreAbove64() {
        val vanillaReceiver = InventoryPlayer(null)
        val inventory = object : InventoryPlayer(null) {
            override fun getInventoryStackLimit(): Int = StackLimitHooks.getCoreInventoryStackSize(vanillaReceiver, super.getInventoryStackLimit())
        }
        val item = item()
        val first = ItemStack(item, 100)
        assertTrue(inventory.addItemStackToInventory(first))
        assertEquals(100, inventory.getStackInSlot(0).count)
        val second = ItemStack(item, 100)
        assertTrue(inventory.addItemStackToInventory(second))
        assertEquals(128, inventory.getStackInSlot(0).count)
        assertEquals(72, inventory.getStackInSlot(1).count)
        assertEquals(200, inventory.mainInventory.sumOf { it.count } + first.count + second.count)
    }

    @Test
    fun offPolicy_listedPlayerOwnerClampsRealInventoryAddWhileUnlistedOwnerExpands() {
        val listedOwner = object : EntityPlayer(
            PlacementWorld(),
            com.mojang.authlib.GameProfile(java.util.UUID(0L, 3L), "listed-owner"),
        ) {
            override fun isSpectator(): Boolean = false
            override fun isCreative(): Boolean = false
        }
        val unlistedOwner = object : EntityPlayer(
            PlacementWorld(),
            com.mojang.authlib.GameProfile(java.util.UUID(0L, 4L), "unlisted-owner"),
        ) {
            override fun isSpectator(): Boolean = false
            override fun isCreative(): Boolean = false
        }
        InventoryStackLimitHook.prepare(256, arrayOf(listedOwner.javaClass.name))
        val dynamicItem = item()
        val listedInventory = object : InventoryPlayer(listedOwner) {
            private val probeStack = ItemStack(dynamicItem, 1)

            override fun getInventoryStackLimit(): Int = StackLimitHooks.resolveInventoryClampLimit(probeStack, this, super.getInventoryStackLimit())
        }
        val unlistedInventory = object : InventoryPlayer(unlistedOwner) {
            private val probeStack = ItemStack(dynamicItem, 1)

            override fun getInventoryStackLimit(): Int = StackLimitHooks.resolveInventoryClampLimit(probeStack, this, super.getInventoryStackLimit())
        }
        val listedSource = ItemStack(dynamicItem, 129)
        val unlistedSource = ItemStack(dynamicItem, 129)

        assertEquals(64, listedInventory.inventoryStackLimit)
        assertEquals(128, unlistedInventory.inventoryStackLimit)
        for (slot in 1 until listedInventory.mainInventory.size) {
            listedInventory.setInventorySlotContents(slot, ItemStack(Blocks.STONE, 64))
            unlistedInventory.setInventorySlotContents(slot, ItemStack(Blocks.STONE, 64))
        }
        assertFalse(listedInventory.addItemStackToInventory(listedSource))
        assertFalse(unlistedInventory.addItemStackToInventory(unlistedSource))
        val listedStored = listedInventory.getStackInSlot(0).count
        val unlistedStored = unlistedInventory.getStackInSlot(0).count
        assertEquals(64, listedStored)
        assertTrue(listedInventory.mainInventory.filterNot(ItemStack::isEmpty).all { it.count <= 64 })
        assertEquals(65, listedSource.count)
        assertEquals(128, unlistedStored)
        assertEquals(1, unlistedSource.count)
        assertEquals(129, listedStored + listedSource.count)
        assertEquals(129, unlistedStored + unlistedSource.count)
    }

    @Test
    fun offPolicy_chestManualPlacementAndRealWrapperInsertExtractConserve() {
        val inventory = chest()
        val item = item()
        val slot = Slot(inventory, 0, 0, 0)
        val manual = ItemStack(item, 100)
        assertEquals(100, minOf(manual.count, ContainerInsertHooks.resolveMergeSlotLimit(slot, manual, slot.slotStackLimit)))
        slot.putStack(manual)
        assertEquals(100, inventory.getStackInSlot(0).count)
        val wrapper = InvWrapper(inventory)
        val remaining = wrapper.insertItem(0, ItemStack(item, 100), false)
        assertEquals(128, inventory.getStackInSlot(0).count)
        assertEquals(72, remaining.count)
        assertEquals(200, inventory.getStackInSlot(0).count + remaining.count)
        assertEquals(128, wrapper.extractItem(0, 128, true).count)
        assertEquals(128, wrapper.extractItem(0, 128, false).count)
        assertTrue(inventory.getStackInSlot(0).isEmpty)
    }

    @Test
    fun offPolicy_containerTransferConservesIntoEmptyAndOccupiedSlots() {
        val inventory = chest()
        val item = item()
        val source = ItemStack(item, 200)
        val slot = object : Slot(inventory, 0, 0, 0) {
            override fun getSlotStackLimit(): Int = ContainerInsertHooks.resolveMergeSlotLimit(this, source, super.getSlotStackLimit())
        }
        val container = object : Container() {
            init {
                addSlotToContainer(slot)
            }
            override fun canInteractWith(player: EntityPlayer): Boolean = true
            fun transfer(): Boolean = mergeItemStack(source, 0, 1, false)
        }
        assertTrue(container.transfer())
        assertEquals(128, inventory.getStackInSlot(0).count)
        assertEquals(72, source.count)
        assertEquals(200, source.count + inventory.getStackInSlot(0).count)
    }

    private class PlacementWorld :
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
    fun offPolicy_emptySlotPickup129RespectsRule128AndRetainsCursorRemainder() {
        val player = object : EntityPlayer(
            PlacementWorld(),
            com.mojang.authlib.GameProfile(java.util.UUID(0L, 1L), "placement"),
        ) {
            override fun isSpectator(): Boolean = false
            override fun isCreative(): Boolean = false
        }
        val inventory = chest()
        val slot = object : Slot(inventory, 0, 0, 0) {
            override fun getItemStackLimit(stack: ItemStack): Int = StackLimitHooks.resolvePlacementSlotLimit(
                stack,
                inventory,
                super.getItemStackLimit(stack),
                inventory.inventoryStackLimit,
            )
        }
        val container = object : Container() {
            init {
                addSlotToContainer(slot)
            }
            override fun canInteractWith(player: EntityPlayer): Boolean = true
        }
        player.inventory.itemStack = ItemStack(item(), 129)
        container.slotClick(0, 0, net.minecraft.inventory.ClickType.PICKUP, player)
        assertEquals(128, inventory.getStackInSlot(0).count)
        assertEquals(1, player.inventory.itemStack.count)
        assertEquals(129, inventory.getStackInSlot(0).count + player.inventory.itemStack.count)
    }

    @Test
    fun placementLimit_preservesSpecialSlotsAndUsesDynamicItemLimitWithNativeGetter() {
        val stack = ItemStack(item(), 129)
        assertEquals(128, StackLimitHooks.resolvePlacementSlotLimit(stack, 256, 256))
        assertEquals(128, StackLimitHooks.resolvePlacementSlotLimit(stack, 256, 64))
        assertEquals(16, StackLimitHooks.resolvePlacementSlotLimit(stack, 16, 256))
        assertEquals(1, StackLimitHooks.resolvePlacementSlotLimit(stack, 1, 256))
    }

    @Test
    fun offPolicy_realEnchantingInventoryBasicSlotClampsRule128ToNative64() {
        verifyRealEnchantingInventoryBasicSlot()
    }

    @Test
    fun onPolicy_realEnchantingInventoryBasicSlotClampsRule128ToNative64() {
        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        InventoryStackLimitHook.prepare(256, null)
        verifyRealEnchantingInventoryBasicSlot()
    }

    @Test
    fun onBlacklistPolicy_unlistedRealEnchantingInventoryBasicSlotClampsRule128ToNative64() {
        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        InventoryStackLimitHook.prepare(256, arrayOf("io.alexjoest.stackupup.dev"))
        verifyRealEnchantingInventoryBasicSlot()
    }

    private fun verifyRealEnchantingInventoryBasicSlot() {
        val world = PlacementWorld()
        val player = object : EntityPlayer(
            world,
            com.mojang.authlib.GameProfile(java.util.UUID(0L, 2L), "enchanting"),
        ) {
            override fun isSpectator(): Boolean = false
            override fun isCreative(): Boolean = false
        }
        val lapis = ENCHANTING_LAPIS_ITEM
        net.minecraftforge.oredict.OreDictionary.registerOre("gemLapis", ItemStack(lapis, 1))
        val container = net.minecraft.inventory.ContainerEnchantment(
            player.inventory,
            world,
            net.minecraft.util.math.BlockPos.ORIGIN,
        )
        val slot = container.inventorySlots[1]
        val offered = ItemStack(lapis, 129)

        assertTrue(slot.isItemValid(offered))
        assertTrue(!StackLimitHooks.shouldBypassInventoryStackLimit(slot.inventory))
        assertTrue(slot.inventory is net.minecraft.inventory.InventoryBasic)
        assertTrue(slot.inventory.javaClass != net.minecraft.inventory.InventoryBasic::class.java)
        assertEquals(64, slot.inventory.inventoryStackLimit)
        assertEquals(128, offered.maxStackSize)
        val advertised = StackLimitHooks.resolvePlacementSlotLimit(
            offered,
            slot.inventory,
            slot.getSlotStackLimit(),
            slot.inventory.inventoryStackLimit,
        )
        assertEquals(64, advertised)
        assertEquals(64, slot.getItemStackLimit(offered))

        player.inventory.itemStack = offered
        container.slotClick(1, 0, net.minecraft.inventory.ClickType.PICKUP, player)

        val initiallyStored = container.tableInventory.getStackInSlot(1).copy()
        val stored = initiallyStored.count
        val remainder = player.inventory.itemStack.count
        assertEquals(64, stored)
        assertEquals(65, remainder)
        assertEquals(129, stored + remainder, "enchanting InventoryBasic writer must conserve offered items")

        val retry = ItemStack(lapis, 1)
        assertEquals(128, retry.maxStackSize)
        assertEquals(
            64,
            StackLimitHooks.resolvePlacementSlotLimit(
                retry,
                slot.inventory,
                slot.getSlotStackLimit(),
                slot.inventory.inventoryStackLimit,
            ),
        )
        player.inventory.itemStack = retry
        container.slotClick(1, 0, net.minecraft.inventory.ClickType.PICKUP, player)

        val storedAfterRetry = container.tableInventory.getStackInSlot(1)
        val storedDelta = storedAfterRetry.count - stored
        val retryRemainder = player.inventory.itemStack.count
        assertTrue(ItemStack.areItemStacksEqual(initiallyStored, storedAfterRetry))
        assertEquals(0, storedDelta)
        assertEquals(1, retryRemainder)
        assertEquals(1, storedDelta + retryRemainder, "a full enchanting slot must retain the attempted item")
    }
}
