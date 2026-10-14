package io.alexjoest.stackupup.mixin

import appeng.tile.inventory.AppEngInternalAEInventory
import appeng.tile.inventory.AppEngInternalInventory
import appeng.util.inv.IAEAppEngInventory
import appeng.util.inv.InvOperation
import io.alexjoest.stackupup.StackLimitHooks
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.IItemHandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Real AE2 target/API contracts plus production owner policy; this JVM does not install Mixins. */
class Ae2NativeOwnerContractTest {
    private var previousMax = 64

    private open class Owner : IAEAppEngInventory {
        override fun saveChanges() = Unit
        override fun onChangeInventory(inv: IItemHandler, slot: Int, operation: InvOperation, removed: ItemStack, added: ItemStack) = Unit
    }

    private class ListedOwner : Owner()
    private class UnlistedOwner : Owner()

    @BeforeEach
    fun prepare() {
        Bootstrap.register()
        previousMax = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 256
        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        InventoryStackLimitHook.prepare(256, arrayOf(ListedOwner::class.java.name))
    }

    @AfterEach
    fun restore() {
        StackUpUpConfig.maxStackSize = previousMax
        resetInventoryExpansionForTesting()
    }

    private fun stack(count: Int): ItemStack = ItemStack(
        Item().setMaxStackSize(256).setRegistryName(ResourceLocation("stackupup_test", "ae2_owner_contract")),
        count,
    )

    private fun resolved(inventory: AppEngInternalInventory): Int =
        StackLimitHooks.getInventoryCompatibilityStackSize(inventory, inventory.tileEntity, inventory.getSlotLimit(0))

    @Test
    fun actualPublicOwnerApi_distinguishesListedAndUnlistedOwnersAndTracksReassignment() {
        val listedOwner = ListedOwner()
        val unlistedOwner = UnlistedOwner()
        val listed = AppEngInternalInventory(listedOwner, 1)
        val unlisted = AppEngInternalInventory(unlistedOwner, 1)
        assertSame(listedOwner, listed.tileEntity)
        assertSame(unlistedOwner, unlisted.tileEntity)
        assertEquals(64, listed.getSlotLimit(0))
        assertEquals(64, unlisted.getSlotLimit(0))
        assertEquals(64, resolved(listed))
        assertEquals(256, resolved(unlisted))
        unlisted.setTileEntity(listedOwner)
        assertEquals(64, resolved(unlisted))
        assertEquals(64, unlisted.getSlotLimit(0))
        unlisted.setTileEntity(unlistedOwner)
        assertEquals(256, resolved(unlisted))
        assertEquals(64, unlisted.getSlotLimit(0))
    }

    @Test
    fun nativeMetadata_remainsOriginalBeforePreparationAndAcrossDelegatingConstructors() {
        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        val owner = UnlistedOwner()
        val inventories = listOf(
            AppEngInternalInventory(owner, 1),
            AppEngInternalInventory(owner, 1, 64),
            AppEngInternalInventory(owner, 1, 64, null),
        )
        inventories.forEach { assertEquals(64, resolved(it)) }
        InventoryStackLimitHook.prepare(256, null)
        inventories.forEach {
            assertEquals(64, it.getSlotLimit(0))
            assertEquals(256, resolved(it))
        }
        for (limit in listOf(1, 16, 256)) {
            val inventory = AppEngInternalInventory(owner, 1, limit)
            assertEquals(limit, resolved(inventory))
            inventory.setTileEntity(ListedOwner())
            assertEquals(limit, resolved(inventory))
        }
    }

    @Test
    fun actualNativeInsertAndExtraction_useNativeGetterCapacityWithoutLoss() {
        for (limit in listOf(1, 16, 64, 256)) {
            val inventory = AppEngInternalInventory(null, 1, limit)
            val offered = stack(300)
            val simulated = inventory.insertItem(0, offered, true)
            assertTrue(inventory.getStackInSlot(0).isEmpty)
            val remainder = inventory.insertItem(0, offered, false)
            assertEquals(limit, inventory.getStackInSlot(0).count)
            assertEquals(300 - limit, remainder.count)
            assertEquals(simulated.count, remainder.count)
            assertEquals(300, inventory.getStackInSlot(0).count + remainder.count)
            assertEquals(limit, inventory.extractItem(0, 300, true).count)
            assertEquals(limit, inventory.extractItem(0, 300, false).count)
            assertTrue(inventory.getStackInSlot(0).isEmpty)
        }
    }

    @Test
    fun actualOwnerChange_preservesOver64ContentsAndSimulatedExtraction() {
        val inventory = AppEngInternalInventory(null, 1)
        inventory.setStackInSlot(0, stack(200))
        inventory.setTileEntity(UnlistedOwner())
        assertEquals(256, resolved(inventory))
        inventory.setTileEntity(ListedOwner())
        assertEquals(64, resolved(inventory))
        assertEquals(200, inventory.getStackInSlot(0).count)
        val remainder = inventory.insertItem(0, stack(1), false)
        assertEquals(1, remainder.count)
        assertEquals(200, inventory.getStackInSlot(0).count)
        assertEquals(200, inventory.extractItem(0, 256, true).count)
        assertEquals(200, inventory.getStackInSlot(0).count)
    }

    @Test
    fun receiverBlacklist_vetoesUnlistedOwnerIndependently() {
        val inventory = AppEngInternalInventory(UnlistedOwner(), 1)
        InventoryStackLimitHook.prepare(256, arrayOf(AppEngInternalInventory::class.java.name))
        assertEquals(64, resolved(inventory))
    }

    @Test
    fun actualAeInventory_retainsOriginalMetadataAndItsSimulatedCapacity() {
        val listedOwner = ListedOwner()
        val listed = AppEngInternalAEInventory(listedOwner, 1)
        val unlistedOwner = UnlistedOwner()
        val unlisted = AppEngInternalAEInventory(unlistedOwner, 1)
        assertEquals(64, listed.getSlotLimit(0))
        assertEquals(64, unlisted.getSlotLimit(0))
        assertEquals(64, StackLimitHooks.getInventoryCompatibilityStackSize(listed, listedOwner, listed.getSlotLimit(0)))
        assertEquals(256, StackLimitHooks.getInventoryCompatibilityStackSize(unlisted, unlistedOwner, unlisted.getSlotLimit(0)))
        val remainder = listed.insertItem(0, stack(129), true)
        assertEquals(65, remainder.count)
        assertTrue(listed.getStackInSlot(0).isEmpty)
        for (limit in listOf(1, 16)) {
            val inventory = AppEngInternalAEInventory(listedOwner, 1, limit)
            assertEquals(limit, inventory.getSlotLimit(0))
            assertEquals(limit, StackLimitHooks.getInventoryCompatibilityStackSize(inventory, listedOwner, inventory.getSlotLimit(0)))
        }
    }
}
