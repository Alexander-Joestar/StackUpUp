package io.alexjoest.stackupup.diagnostic

import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

internal sealed class InspectSelector {
    abstract val slot: Int?

    data class OpenContainer(override val slot: Int? = null) : InspectSelector()

    data class BlockInventory(val position: BlockPos, val side: EnumFacing?, override val slot: Int? = null) : InspectSelector()

    companion object {
        fun parse(args: Array<out String>): InspectSelector {
            require(args.firstOrNull() == "inspect")
            if (args.size == 1) return OpenContainer()
            if (args.size == 3 && args[1] == "slot") return OpenContainer(parseSlot(args[2]))
            require(args.size == 6 || args.size == 8)
            require(args[1] == "block")
            val coordinates = args.slice(2..4).map { it.toIntOrNull() ?: throw IllegalArgumentException("coordinate") }
            val side = if (args[5] == "none") {
                null
            } else {
                EnumFacing.entries.firstOrNull { it.name.lowercase() == args[5] }
                    ?: throw IllegalArgumentException("side")
            }
            val slot = if (args.size == 8) {
                require(args[6] == "slot")
                parseSlot(args[7])
            } else {
                null
            }
            return BlockInventory(BlockPos(coordinates[0], coordinates[1], coordinates[2]), side, slot)
        }

        private fun parseSlot(value: String): Int = (value.toIntOrNull() ?: throw IllegalArgumentException("slot")).also { require(it >= 0) }
    }
}
