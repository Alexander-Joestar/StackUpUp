package io.alexjoest.stackupup

import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import net.minecraft.init.Bootstrap
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.SlotItemHandler
import net.minecraftforge.items.wrapper.InvWrapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SelectiveInventoryCapacityBehaviorTest {
    private var previousMax = 64

    private open class ListedInventory : InventoryBasic("listed", false, 1) {
        override fun getInventoryStackLimit(): Int = StackLimitHooks.getInventoryCompatibilityStackSize(this, super.getInventoryStackLimit())
    }

    private class RelatedInventory : ListedInventory()

    private interface ListedContract

    private class ContractAddonInventory :
        InventoryBasic("contract-addon", false, 1),
        ListedContract {
        override fun getInventoryStackLimit(): Int = StackLimitHooks.getInventoryCompatibilityStackSize(this, super.getInventoryStackLimit())
    }

    private class UnrelatedPrefixInventory : InventoryBasic("prefix", false, 1) {
        override fun getInventoryStackLimit(): Int = StackLimitHooks.getInventoryCompatibilityStackSize(this, super.getInventoryStackLimit())
    }

    private class UnlistedInventory : InventoryBasic("unlisted", false, 1) {
        override fun getInventoryStackLimit(): Int = StackLimitHooks.getInventoryCompatibilityStackSize(this, super.getInventoryStackLimit())
    }

    @BeforeEach
    fun prepare() {
        Bootstrap.register()
        previousMax = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 256
        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        StackLimitHooks.refreshInventoryStackLimitBlacklist(
            listOf("listed_mod"),
            mapOf("listed_mod" to listOf(ListedInventory::class.java.name), "unlisted_mod" to listOf(UnlistedInventory::class.java.name)),
        )
        io.alexjoest.stackupup.limit.RuleRuntime.replaceSnapshot(
            io.alexjoest.stackupup.rules.compile.RuleSnapshot(1L, listOf(io.alexjoest.stackupup.rules.compile.RuleCompiler.compileLine("size >= 1 -> 256", 1))),
        )
    }

    @AfterEach
    fun restore() {
        StackUpUpConfig.maxStackSize = previousMax
        io.alexjoest.stackupup.limit.RuleRuntime.replaceSnapshot(io.alexjoest.stackupup.rules.compile.RuleSnapshot(0L, emptyList()))
        resetInventoryExpansionForTesting()
    }

    private fun stack(count: Int): ItemStack {
        val item = object : Item() {
            override fun getItemStackLimit(stack: ItemStack): Int =
                if (StackLimitHooks.shouldBypassDynamicItemRules()) 64 else StackLimitHooks.applyDynamicStackLimit(stack, 64)
        }.setRegistryName(ResourceLocation("stackupup_test", "selective_capacity"))
        return ItemStack(item, count)
    }

    @Test
    fun listedAAndUnlistedB_coexistThroughRealWrapperInsertAndExtract() {
        val listed = ListedInventory()
        val unlisted = UnlistedInventory()
        assertEquals(64, listed.inventoryStackLimit)
        assertEquals(256, unlisted.inventoryStackLimit)
        assertTrue(InventoryStackLimitHook.isExpansionEnabled())
        for ((inventory, expected) in listOf(listed to 64, unlisted to 200)) {
            val wrapper = InvWrapper(inventory)
            val offered = stack(200)
            val simulated = wrapper.insertItem(0, offered, true)
            assertTrue(inventory.getStackInSlot(0).isEmpty)
            val remainder = wrapper.insertItem(0, offered, false)
            assertEquals(expected, inventory.getStackInSlot(0).count)
            assertEquals(200 - expected, remainder.count)
            assertEquals(simulated.count, remainder.count)
            assertEquals(200, inventory.getStackInSlot(0).count + remainder.count)
            val slot = SlotItemHandler(wrapper, 0, 0, 0)
            assertTrue(slot.getItemStackLimit(offered) <= inventory.inventoryStackLimit)
            assertEquals(expected, wrapper.extractItem(0, 200, true).count)
            assertEquals(expected, wrapper.extractItem(0, 200, false).count)
            assertTrue(inventory.getStackInSlot(0).isEmpty)
            slot.putStack(stack(expected))
            assertEquals(expected, inventory.getStackInSlot(0).count)
        }
    }

    @Test
    fun listedClass_onlyItsExactReceiverIsVetoed() {
        StackLimitHooks.refreshInventoryStackLimitBlacklist(
            listOf("listed_mod"),
            mapOf("listed_mod" to listOf(ListedInventory::class.java.name)),
        )
        assertEquals(64, ListedInventory().inventoryStackLimit)
        assertTrue(StackLimitHooks.shouldBypassInventoryStackLimit(ListedInventory()))
        assertEquals(256, RelatedInventory().inventoryStackLimit)
        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(RelatedInventory()))
        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(UnlistedInventory()))
        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(ContractAddonInventory()))
        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(UnrelatedPrefixInventory()))
        assertEquals(256, UnlistedInventory().inventoryStackLimit)
        assertEquals(256, ContractAddonInventory().inventoryStackLimit)
        assertEquals(256, UnrelatedPrefixInventory().inventoryStackLimit)
    }

    @Test
    fun listedInterface_doesNotVetoAnUnlistedImplementation() {
        InventoryStackLimitHook.prepare(256, arrayOf(ListedContract::class.java.name))
        assertTrue(InventoryStackLimitHook.isBlacklisted(ListedContract::class.java))
        val addon = ContractAddonInventory()
        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(addon))
        assertEquals(256, addon.inventoryStackLimit)
        val remainder = InvWrapper(addon).insertItem(0, stack(200), false)
        assertEquals(200, addon.getStackInSlot(0).count)
        assertTrue(remainder.isEmpty)
    }

    @Test
    fun emptyBlacklist_preservesAllReceiverAndReceiverlessExpansion() {
        InventoryStackLimitHook.prepare(256, emptyArray())
        for (target in listOf(ListedInventory(), RelatedInventory(), UnlistedInventory(), ContractAddonInventory())) {
            assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(target))
            assertEquals(256, target.inventoryStackLimit)
            assertEquals(
                256,
                StackLimitHooks.resolveInventoryWriteLimit(stack(200), target, target.inventoryStackLimit),
            )
        }
        assertEquals(256, StackLimitHooks.getInventoryCompatibilityStackSize(64))
    }

    @Test
    fun sharedReceiver_hasNoCreatorAttributionAndPublicDelegateRemainsSelective() {
        val shared = net.minecraftforge.items.ItemStackHandler(1)
        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(shared))
        assertEquals(256, StackLimitHooks.getInventoryCompatibilityStackSize(shared, 64))
        assertEquals(256, StackLimitHooks.getInventoryCompatibilityStackSize(64))
        assertEquals(64, InventoryStackLimitHook.resolveInventoryStackLimit(null as Class<*>?, 64))
        assertFalse(InventoryStackLimitHook.isBlacklisted(null))
        assertTrue(StackLimitHooks.shouldBypassInventoryStackLimit(InvWrapper(ListedInventory())))
        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(InvWrapper(UnlistedInventory())))
        assertEquals(64, StackLimitHooks.getInventoryCompatibilityStackSize(shared, ListedInventory(), 64))
        assertEquals(256, StackLimitHooks.getInventoryCompatibilityStackSize(shared, UnlistedInventory(), 64))
    }

    @Test
    fun publicationInvalidatesCachedClassResults() {
        val target = ListedInventory()
        assertTrue(StackLimitHooks.shouldBypassInventoryStackLimit(target))
        InventoryStackLimitHook.prepare(256, null)
        assertFalse(StackLimitHooks.shouldBypassInventoryStackLimit(target))
        assertEquals(256, target.inventoryStackLimit)
    }

    @Test
    fun excluded64ClampCannotBeReexpandedByItemRules() {
        assertEquals(64, StackLimitHooks.resolveInventoryClampLimit(stack(200), ListedInventory().inventoryStackLimit))
        assertEquals(256, StackLimitHooks.resolveInventoryClampLimit(stack(200), UnlistedInventory().inventoryStackLimit))
        assertEquals(16, StackLimitHooks.resolveInventoryClampLimit(stack(200), 16))
    }

    @Test
    fun listedAndUnlisted_directWriteLimitsRemainSelective() {
        val listed = ListedInventory()
        val unlisted = UnlistedInventory()

        assertEquals(64, StackLimitHooks.resolveInventoryWriteLimit(stack(200), listed, listed.inventoryStackLimit))
        assertEquals(256, StackLimitHooks.resolveInventoryWriteLimit(stack(200), unlisted, unlisted.inventoryStackLimit))
    }

    @Test
    fun listedAndUnlisted_containerMergeLimitsRemainSelective() {
        val listed = ListedInventory()
        val unlisted = UnlistedInventory()
        val listedSlot = Slot(listed, 0, 0, 0)
        val unlistedSlot = Slot(unlisted, 0, 0, 0)

        assertEquals(64, ContainerInsertHooks.resolveMergeSlotLimit(listedSlot, stack(200), 64))
        assertEquals(256, ContainerInsertHooks.resolveMergeSlotLimit(unlistedSlot, stack(200), 64))
    }

    @Test
    fun listedAndUnlisted_slotPlacementLimitsRemainSelective() {
        val listed = ListedInventory()
        val unlisted = UnlistedInventory()

        assertEquals(
            64,
            StackLimitHooks.resolvePlacementSlotLimit(stack(200), listed, 64, listed.inventoryStackLimit),
        )
        assertEquals(
            256,
            StackLimitHooks.resolvePlacementSlotLimit(stack(200), unlisted, 64, unlisted.inventoryStackLimit),
        )
    }

    @Test
    fun lowerItemRuleRemainsEffectiveAcrossPlacementAndMerge() {
        io.alexjoest.stackupup.limit.RuleRuntime.replaceSnapshot(
            io.alexjoest.stackupup.rules.compile.RuleSnapshot(
                2L,
                listOf(io.alexjoest.stackupup.rules.compile.RuleCompiler.compileLine("size >= 1 -> 16", 1)),
            ),
        )
        val inventory = UnlistedInventory()
        val stack = stack(200)

        assertEquals(
            16,
            StackLimitHooks.resolvePlacementSlotLimit(stack, inventory, 64, inventory.inventoryStackLimit),
        )
        assertEquals(
            16,
            ContainerInsertHooks.resolveMergeSlotLimit(Slot(inventory, 0, 0, 0), stack, 64),
        )
    }

    @Test
    fun loadedModWithoutPackagesFailsAndAbsentModHasNoEffect() {
        assertThrows(IllegalStateException::class.java) {
            StackLimitHooks.refreshInventoryStackLimitBlacklist(listOf("listed_mod"), mapOf("listed_mod" to emptyList()))
        }
        StackLimitHooks.refreshInventoryStackLimitBlacklist(listOf("absent_mod"), emptyMap())
        assertEquals(256, ListedInventory().inventoryStackLimit)
        assertEquals(256, UnlistedInventory().inventoryStackLimit)
    }
}
