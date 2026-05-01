package io.alexjoest.stackupup

import net.minecraft.init.Bootstrap
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * InventoryBasic.setInventorySlotContents 会截断到 getInventoryStackLimit() (64)，
 * 因此所有 "期望槽位数量 >= N" 的断言，测试数量必须 <= 64。
 */
class ClientSlotSyncHooksTest {
    companion object {
        private val TEST_ITEM = Item()
        private val OTHER_ITEM = Item()
    }

    @BeforeEach
    fun setUpBootstrap() {
        Bootstrap.register()
    }

    // ── restoreContainerSlotStackCount ──────────────────────────────────

    @Test
    fun `restore count when slot has fewer items than transmitted`() {
        val (container, _) = createContainerWithSlot(TEST_ITEM, 1)
        val transmitted = ItemStack(TEST_ITEM, 60)

        ClientSlotSyncHooks.restoreContainerSlotStackCount(container, 0, transmitted, 60)

        assertEquals(60, container.getSlot(0).stack.count)
    }

    @Test
    fun `do not restore when slot already has equal or higher count`() {
        val (container, _) = createContainerWithSlot(TEST_ITEM, 64)
        val transmitted = ItemStack(TEST_ITEM, 32)

        ClientSlotSyncHooks.restoreContainerSlotStackCount(container, 0, transmitted, 32)

        assertEquals(64, container.getSlot(0).stack.count)
    }

    @Test
    fun `do not restore empty transmitted stack`() {
        val (container, _) = createContainerWithSlot(TEST_ITEM, 64)
        val transmitted = ItemStack.EMPTY

        ClientSlotSyncHooks.restoreContainerSlotStackCount(container, 0, transmitted, 0)

        assertEquals(64, container.getSlot(0).stack.count)
    }

    @Test
    fun `do not restore when transmitted count is zero`() {
        val (container, _) = createContainerWithSlot(TEST_ITEM, 64)
        val transmitted = ItemStack(TEST_ITEM, 0)

        ClientSlotSyncHooks.restoreContainerSlotStackCount(container, 0, transmitted, 0)

        assertEquals(64, container.getSlot(0).stack.count)
    }

    @Test
    fun `do not restore when slot stack type differs from transmitted`() {
        val (container, _) = createContainerWithSlot(TEST_ITEM, 1)
        val transmitted = ItemStack(OTHER_ITEM, 60)

        ClientSlotSyncHooks.restoreContainerSlotStackCount(container, 0, transmitted, 60)

        assertEquals(1, container.getSlot(0).stack.count)
    }

    @Test
    fun `do nothing for out-of-range slot id`() {
        val (container, _) = createContainerWithSlot(TEST_ITEM, 1)
        val transmitted = ItemStack(TEST_ITEM, 60)

        ClientSlotSyncHooks.restoreContainerSlotStackCount(container, -1, transmitted, 60)
        ClientSlotSyncHooks.restoreContainerSlotStackCount(container, 999, transmitted, 60)
    }

    @Test
    fun `restore count for empty slot using transmitted stack`() {
        val container = TestContainer()
        container.addSlotToContainer(Slot(InventoryBasic("test", false, 1), 0, 0, 0))
        val transmitted = ItemStack(TEST_ITEM, 60)

        ClientSlotSyncHooks.restoreContainerSlotStackCount(container, 0, transmitted, 60)

        assertEquals(60, container.getSlot(0).stack.count)
    }

    // ── restoreContainerSlotStackCounts ─────────────────────────────────

    @Test
    fun `restore multiple slot counts from list`() {
        val (container, _) = createContainerWithSlot(TEST_ITEM, 1)
        val inv2 = InventoryBasic("test2", false, 1)
        inv2.setInventorySlotContents(0, ItemStack(TEST_ITEM, 2))
        container.addSlotToContainer(Slot(inv2, 0, 0, 0))

        val transmitted = listOf(
            ItemStack(TEST_ITEM, 60),
            ItemStack(TEST_ITEM, 55),
        )

        ClientSlotSyncHooks.restoreContainerSlotStackCounts(container, transmitted)

        assertEquals(60, container.getSlot(0).stack.count)
        assertEquals(55, container.getSlot(1).stack.count)
    }

    @Test
    fun `bulk restore handles empty list gracefully`() {
        val (container, _) = createContainerWithSlot(TEST_ITEM, 50)

        ClientSlotSyncHooks.restoreContainerSlotStackCounts(container, emptyList())

        assertEquals(50, container.getSlot(0).stack.count)
    }

    @Test
    fun `bulk restore handles partial list shorter than slots`() {
        val (container, _) = createContainerWithSlot(TEST_ITEM, 1)
        val inv2 = InventoryBasic("test2", false, 1)
        inv2.setInventorySlotContents(0, ItemStack(TEST_ITEM, 2))
        container.addSlotToContainer(Slot(inv2, 0, 0, 0))

        val transmitted = listOf(ItemStack(TEST_ITEM, 60))

        ClientSlotSyncHooks.restoreContainerSlotStackCounts(container, transmitted)

        assertEquals(60, container.getSlot(0).stack.count)
        assertEquals(2, container.getSlot(1).stack.count)
    }

    @Test
    fun `bulk restore ignores different item ids`() {
        val (container, _) = createContainerWithSlot(TEST_ITEM, 1)
        val inv2 = InventoryBasic("test2", false, 1)
        val slot2 = Slot(inv2, 0, 0, 0)
        slot2.putStack(ItemStack(OTHER_ITEM, 2))
        container.addSlotToContainer(slot2)

        val transmitted = listOf(
            ItemStack(TEST_ITEM, 60),
            ItemStack(TEST_ITEM, 55),
        )

        ClientSlotSyncHooks.restoreContainerSlotStackCounts(container, transmitted)

        assertEquals(60, container.getSlot(0).stack.count)
        assertEquals(2, container.getSlot(1).stack.count)
    }

    // ── RemainerGuard integration ───────────────────────────────────────

    @Test
    fun `remainder calculation via guard respects disabled state`() {
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
        val attempted = ItemStack(TEST_ITEM, 200)

        RemainderGuard.withoutRemainder {
            slot.putStack(attempted.copy())
        }

        val remainder = ContainerInsertHooks.remainderCountAfterEmptyPut(slot, attempted)
        assertEquals(136, remainder)
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun createContainerWithSlot(item: Item, count: Int): Pair<TestContainer, InventoryBasic> {
        val inventory = InventoryBasic("test", false, 1)
        val slot = Slot(inventory, 0, 0, 0)
        slot.putStack(ItemStack(item, count))
        val container = TestContainer()
        container.addSlotToContainer(slot)
        return container to inventory
    }
}
