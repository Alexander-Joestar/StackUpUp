package io.alexjoest.stackupup.diagnostic

import io.alexjoest.stackupup.rules.RuleMessageKey
import net.minecraft.command.CommandException
import net.minecraft.command.ICommandSender
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.inventory.Container
import net.minecraft.inventory.IInventory
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.SlotItemHandler
import net.minecraftforge.items.wrapper.InvWrapper
import java.util.IdentityHashMap

internal data class InspectionOwnership(val modId: String, val packages: Set<String>)

internal data class InspectedObject(val id: Int, val role: String, val target: Any, val modCandidates: List<String>, val blacklistTarget: Boolean) {
    val className: String get() = target.javaClass.name
    val confirmedModId: String? get() = modCandidates.singleOrNull()
    val identity: String get() = "$className@${Integer.toHexString(System.identityHashCode(target))}"
}

internal data class InspectedSlot(val index: Int, val backingIndex: Int, val slotObjectId: Int?, val receiverObjectId: Int)

internal data class InspectionReport(val objects: List<InspectedObject>, val slots: List<InspectedSlot>, val omittedSlots: Int, val unknownDelegate: Boolean)

internal class InventoryInspection(private val ownership: List<InspectionOwnership>) {
    fun inspect(sender: ICommandSender, selector: InspectSelector): InspectionReport = when (selector) {
        is InspectSelector.OpenContainer -> {
            val player = sender.commandSenderEntity as? EntityPlayerMP
                ?: throw CommandException(RuleMessageKey.COMMAND_INSPECT_PLAYER_REQUIRED.translationKey)
            inspectContainer(player.openContainer ?: throw noTarget(), selector.slot)
        }
        is InspectSelector.BlockInventory -> {
            val world = sender.entityWorld
            if (!world.isBlockLoaded(selector.position)) throw noTarget()
            val tile = world.getTileEntity(selector.position) ?: throw noTarget()
            inspectTile(tile, selector.side, selector.slot)
        }
    }

    fun inspectContainer(container: Container, selectedSlot: Int? = null): InspectionReport {
        val builder = ReportBuilder()
        builder.add("Container", container)
        val indices = selectedIndices(container.inventorySlots.size, selectedSlot)
        for (index in indices) {
            val slot = container.inventorySlots[index]
            val slotId = builder.add("Slot", slot)
            val receiverId = if (slot is SlotItemHandler) {
                builder.handler(slot.itemHandler)
            } else {
                builder.add("IInventory", slot.inventory, true)
            }
            builder.slots.add(InspectedSlot(index, slot.slotIndex, slotId, receiverId))
        }
        return builder.finish(container.inventorySlots.size - indices.size)
    }

    fun inspectTile(tile: TileEntity, side: EnumFacing?, selectedSlot: Int? = null): InspectionReport {
        val capability = CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
        val handler = if (capability == null) null else tile.getCapability(capability, side)
        if (handler == null && tile !is IInventory) throw noTarget()
        val builder = ReportBuilder()
        builder.add("TileEntity", tile, tile is IInventory)
        val receiver = if (handler != null) builder.handler(handler) else builder.add("IInventory", tile, true)
        val size = handler?.slots ?: (tile as IInventory).sizeInventory
        val indices = selectedIndices(size, selectedSlot)
        indices.forEach { builder.slots.add(InspectedSlot(it, it, null, receiver)) }
        return builder.finish(size - indices.size)
    }

    fun candidates(className: String): List<String> = ownership.filter { owner ->
        owner.packages.any { packageName -> packageName.isNotEmpty() && className.startsWith("$packageName.") }
    }.map { it.modId }.distinct().sorted()

    private fun selectedIndices(size: Int, slot: Int?): List<Int> {
        if (slot != null) {
            if (slot !in 0 until size) throw CommandException(RuleMessageKey.COMMAND_INSPECT_SLOT_RANGE.translationKey, slot, size)
            return listOf(slot)
        }
        return (0 until minOf(size, MAX_SLOTS)).toList()
    }

    private inner class ReportBuilder {
        val objects = mutableListOf<InspectedObject>()
        val slots = mutableListOf<InspectedSlot>()
        private val ids = IdentityHashMap<Any, Int>()
        private var unknownDelegate = false

        fun add(role: String, target: Any, blacklist: Boolean = false): Int {
            val existing = ids[target]
            if (existing != null) return existing
            val id = objects.size
            ids[target] = id
            objects.add(InspectedObject(id, role, target, candidates(target.javaClass.name), blacklist))
            return id
        }

        fun handler(handler: IItemHandler): Int {
            val id = add("IItemHandler", handler, true)
            if (handler is InvWrapper) {
                add("InvWrapper.getInv()", handler.inv, true)
            } else {
                unknownDelegate = true
            }
            return id
        }

        fun finish(omitted: Int): InspectionReport = InspectionReport(objects.toList(), slots.toList(), omitted, unknownDelegate)
    }

    companion object {
        const val MAX_SLOTS = 8

        fun loaded(): InventoryInspection = InventoryInspection(
            Loader.instance().modList.map { InspectionOwnership(it.modId, it.ownedPackages.toSet()) },
        )

        private fun noTarget(): CommandException = CommandException(RuleMessageKey.COMMAND_INSPECT_NO_TARGET.translationKey)
    }
}
