package io.alexjoest.stackupup.core

import net.minecraft.init.Bootstrap
import net.minecraft.inventory.InventoryBasic
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.IItemHandlerModifiable
import net.minecraftforge.items.wrapper.CombinedInvWrapper
import net.minecraftforge.items.wrapper.InvWrapper
import net.minecraftforge.items.wrapper.RangedWrapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class Ae2ItemHandlerInsertLimiterTest {
    @Test
    fun `insertCapped_shouldSplitUnknownHandlerIntoVanillaSizedCalls`() {
        val handler = RecordingHandler(slotLimit = 256)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(150), false)

        assertTrue(result.isEmpty)
        assertEquals(listOf(64, 64, 22), handler.calls.map { it.count })
        assertEquals(listOf(false, false, false), handler.calls.map { it.simulate })
    }

    @Test
    fun `insertCapped_shouldRespectSlotLimitBelowVanillaLimit`() {
        val handler = RecordingHandler(slotLimit = 32)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(80), true)

        assertEquals(listOf(32), handler.calls.map { it.count })
        assertEquals(listOf(true), handler.calls.map { it.simulate })
        assertEquals(48, result.count)
    }

    @Test
    fun `insertCapped_shouldNeverPassOversizedStackToUnsafeHandler`() {
        val handler = RecordingHandler(slotLimit = 64)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(130), false)

        assertTrue(result.isEmpty)
        assertEquals(listOf(64, 64, 2), handler.calls.map { it.count })
        assertTrue(handler.calls.all { it.count <= 64 })
    }

    @Test
    fun `insertCapped_shouldNotInsertWhenSlotLimitIsZero`() {
        val original = stack(80)
        val handler = RecordingHandler(slotLimit = 0)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, original, false)

        assertEquals(80, result.count)
        assertEquals(emptyList<Call>(), handler.calls)
    }

    @Test
    fun `insertCapped_shouldTrustFixedForgeInventoryWrapper`() {
        val handler = RecordingInvWrapper(slotLimit = 256)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(150), false)

        assertTrue(result.isEmpty)
        assertEquals(listOf(150), handler.calls.map { it.count })
    }

    @Test
    fun `insertCapped_shouldNotTrustCombinedWrapperAroundUnknownHandler`() {
        val inner = RecordingModifiableHandler(slotLimit = 256)
        val handler = CombinedInvWrapper(inner)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(150), false)

        assertTrue(result.isEmpty)
        assertEquals(listOf(64, 64, 22), inner.calls.map { it.count })
        assertTrue(inner.calls.all { it.count <= 64 })
    }

    @Test
    fun `insertCapped_shouldNotTrustRangedWrapperAroundUnknownHandler`() {
        val inner = RecordingModifiableHandler(slotLimit = 256)
        val handler = RangedWrapper(inner, 0, 1)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(150), false)

        assertTrue(result.isEmpty)
        assertEquals(listOf(64, 64, 22), inner.calls.map { it.count })
        assertTrue(inner.calls.all { it.count <= 64 })
    }

    @Test
    fun `insertCapped_shouldOnlySimulateOneChunkForUntrustedHandler`() {
        val handler = CapacityHandler(slotLimit = 256, capacity = 64)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(128), true)

        assertEquals(0, handler.stored)
        assertEquals(listOf(Call(64, true)), handler.calls)
        assertEquals(64, result.count)
    }

    @Test
    fun `insertCapped_shouldReturnRealRemainderAfterChunkedInsertionFillsSlot`() {
        val handler = CapacityHandler(slotLimit = 256, capacity = 64)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(128), false)

        assertEquals(64, result.count)
        assertEquals(64, handler.stored)
        assertEquals(listOf(Call(64, false), Call(64, false)), handler.calls)
    }

    @Test
    fun `insertCapped_shouldPreserveRemainderWhenChunkIsPartiallyAccepted`() {
        val handler = CapacityHandler(slotLimit = 256, capacity = 40)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(100), false)

        assertEquals(60, result.count)
        assertEquals(40, handler.stored)
        assertEquals(listOf(Call(64, false)), handler.calls)
    }

    private class RecordingHandler(private val slotLimit: Int) : IItemHandler {
        val calls = mutableListOf<Call>()

        override fun getSlots(): Int = 1

        override fun getStackInSlot(slot: Int): ItemStack = ItemStack.EMPTY

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            calls += Call(stack.count, simulate)
            return ItemStack.EMPTY
        }

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = ItemStack.EMPTY

        override fun getSlotLimit(slot: Int): Int = slotLimit
    }

    private class CapacityHandler(
        private val slotLimit: Int,
        private val capacity: Int,
    ) : IItemHandler {
        val calls = mutableListOf<Call>()
        var stored = 0

        override fun getSlots(): Int = 1

        override fun getStackInSlot(slot: Int): ItemStack = if (stored > 0) stack(stored) else ItemStack.EMPTY

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            calls += Call(stack.count, simulate)
            val accepted = minOf(stack.count, capacity - stored)
            if (!simulate) {
                stored += accepted
            }
            if (accepted >= stack.count) {
                return ItemStack.EMPTY
            }
            return stack(stack.count - accepted)
        }

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = ItemStack.EMPTY

        override fun getSlotLimit(slot: Int): Int = slotLimit
    }

    private class RecordingModifiableHandler(private val slotLimit: Int) : IItemHandlerModifiable {
        val calls = mutableListOf<Call>()

        override fun getSlots(): Int = 1

        override fun getStackInSlot(slot: Int): ItemStack = ItemStack.EMPTY

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            calls += Call(stack.count, simulate)
            return ItemStack.EMPTY
        }

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = ItemStack.EMPTY

        override fun getSlotLimit(slot: Int): Int = slotLimit

        override fun setStackInSlot(slot: Int, stack: ItemStack) {
        }
    }

    private class RecordingInvWrapper(slotLimit: Int) : InvWrapper(LimitedInventory(slotLimit)) {
        val calls = mutableListOf<Call>()

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            calls += Call(stack.count, simulate)
            return super.insertItem(slot, stack, simulate)
        }
    }

    private class LimitedInventory(private val stackLimit: Int) : InventoryBasic("stackupup-test-wrapper", false, 1) {
        override fun getInventoryStackLimit(): Int = stackLimit
    }

    private data class Call(val count: Int, val simulate: Boolean)

    companion object {
        private lateinit var testItem: Item

        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            Bootstrap.register()
            testItem = Item()
                .setMaxStackSize(256)
                .setRegistryName(ResourceLocation("stackupup_test", "ae2_limiter_item"))
        }

        private fun stack(count: Int): ItemStack = ItemStack(testItem, count)
    }
}
