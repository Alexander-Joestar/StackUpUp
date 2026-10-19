package io.alexjoest.stackupup.diagnostic

import net.minecraft.command.CommandException
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Bootstrap
import net.minecraft.init.Items
import net.minecraft.inventory.Container
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.SlotItemHandler
import net.minecraftforge.items.wrapper.InvWrapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class InventoryInspectionTest {
    private val inspection = InventoryInspection(emptyList())

    @Test
    fun container_shouldResolveRealSlotsInventoriesAndPublicWrapperDelegateWithoutWrites() {
        val inventory = InventoryBasic("test", false, 2)
        inventory.setInventorySlotContents(1, ItemStack(Items.APPLE, 17))
        val wrapper = ReadOnlyWrapper(inventory)
        val vanillaSlot = Slot(inventory, 1, 0, 0)
        val handlerSlot = SlotItemHandler(wrapper, 1, 0, 0)
        val report = inspection.inspectContainer(TestContainer(vanillaSlot, handlerSlot))

        assertSame(vanillaSlot, report.objects[report.slots[0].slotObjectId!!].target)
        assertSame(inventory, report.objects[report.slots[0].receiverObjectId].target)
        assertSame(wrapper, report.objects[report.slots[1].receiverObjectId].target)
        assertEquals(1, report.slots[1].backingIndex)
        assertEquals(1, report.objects.count { it.target === inventory })
        assertFalse(report.unknownDelegate)
        assertEquals(17, inventory.getStackInSlot(1).count)
    }

    @Test
    fun container_shouldBoundDefaultOutputAndRejectOutOfRangeSlots() {
        val inventory = InventoryBasic("test", false, 20)
        val container = TestContainer(*(0 until 20).map { Slot(inventory, it, 0, 0) }.toTypedArray())
        val report = inspection.inspectContainer(container)
        assertEquals(InventoryInspection.MAX_SLOTS, report.slots.size)
        assertEquals(12, report.omittedSlots)
        assertEquals(19, inspection.inspectContainer(container, 19).slots.single().index)
        assertThrows(CommandException::class.java) { inspection.inspectContainer(container, 20) }
        assertThrows(CommandException::class.java) { inspection.inspectContainer(container, -1) }
        assertEquals(1, report.objects.count { it.target === inventory })
    }

    @Test
    fun opaqueHandler_shouldRemainUnknownAndNeverProbeInsertion() {
        val handler = ReadOnlyHandler()
        val report = inspection.inspectContainer(TestContainer(SlotItemHandler(handler, 0, 0, 0)))
        assertTrue(report.unknownDelegate)
        assertSame(handler, report.objects[report.slots.single().receiverObjectId].target)
        assertFalse(report.objects.any { it.role == "IInventory" })
    }

    @Test
    fun tile_withoutCapability_shouldFailFastWhenNoInventoryExists() {
        assertThrows(CommandException::class.java) { inspection.inspectTile(EmptyTile(), EnumFacing.UP) }
    }

    @Test
    fun ownership_shouldUseExactPackageBoundariesAndKeepOverlapAmbiguous() {
        val resolver = InventoryInspection(
            listOf(
                InspectionOwnership("first", setOf("example.inventory")),
                InspectionOwnership("second", setOf("example.inventory.child")),
            ),
        )
        assertEquals(listOf("first"), resolver.candidates("example.inventory.Store"))
        assertEquals(emptyList<String>(), resolver.candidates("example.inventoryOther.Store"))
        assertEquals(listOf("first", "second"), resolver.candidates("example.inventory.child.Store"))
        assertNull(InspectedObject(0, "IInventory", Any(), resolver.candidates("example.inventory.child.Store"), true).confirmedModId)
        assertEquals(emptyList<String>(), resolver.candidates("net.minecraft.inventory.InventoryBasic"))
    }

    @Test
    fun selectors_shouldParseExactSyntaxAndRejectBadArguments() {
        assertEquals(InspectSelector.OpenContainer(), InspectSelector.parse(arrayOf("inspect")))
        assertEquals(InspectSelector.OpenContainer(3), InspectSelector.parse(arrayOf("inspect", "slot", "3")))
        assertEquals(
            InspectSelector.BlockInventory(BlockPos(-1, 64, 2), EnumFacing.NORTH, 0),
            InspectSelector.parse(arrayOf("inspect", "block", "-1", "64", "2", "north", "slot", "0")),
        )
        assertNull((InspectSelector.parse(arrayOf("inspect", "block", "0", "64", "0", "none")) as InspectSelector.BlockInventory).side)
        for (args in listOf(
            arrayOf("inspect", "slot", "-1"),
            arrayOf("inspect", "slot", "2147483648"),
            arrayOf("inspect", "slot", "1", "extra"),
            arrayOf("inspect", "block", "~", "64", "0", "none"),
            arrayOf("inspect", "block", "0", "64", "0", "invalid"),
        )) {
            assertThrows(IllegalArgumentException::class.java) { InspectSelector.parse(args) }
        }
    }

    private class TestContainer(vararg slots: Slot) : Container() {
        init {
            slots.forEach { addSlotToContainer(it) }
        }

        override fun canInteractWith(playerIn: EntityPlayer): Boolean = true
    }

    private class ReadOnlyWrapper(inventory: InventoryBasic) : InvWrapper(inventory) {
        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack = error("Inspection must not insert")
        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = error("Inspection must not extract")
        override fun setStackInSlot(slot: Int, stack: ItemStack): Unit = error("Inspection must not write")
        override fun getSlotLimit(slot: Int): Int = error("Inspection must not probe capacity")
    }

    private class ReadOnlyHandler : ItemStackHandler(1) {
        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack = error("Inspection must not insert")
        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = error("Inspection must not extract")
        override fun setStackInSlot(slot: Int, stack: ItemStack): Unit = error("Inspection must not write")
        override fun getSlotLimit(slot: Int): Int = error("Inspection must not probe capacity")
    }

    private class EmptyTile : TileEntity()

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            Bootstrap.register()
        }
    }
}
