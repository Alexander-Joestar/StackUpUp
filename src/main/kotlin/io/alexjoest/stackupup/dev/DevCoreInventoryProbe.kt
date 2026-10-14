package io.alexjoest.stackupup.dev

import com.mojang.authlib.GameProfile
import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.StackUpUpConfig
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.ContainerChest
import net.minecraft.inventory.ContainerPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.management.PlayerInteractionManager
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.math.BlockPos
import net.minecraftforge.items.wrapper.InvWrapper
import java.util.UUID

internal const val CORE_INVENTORY_FOCUS = "core_inventory_off"

internal fun validateFocusedRequest(focus: String, matrix: Boolean, compatIds: Set<String>) {
    require(focus in focusedProbeIds) { "unknown_focus: $focus" }
    require(!matrix) { "focus_matrix_conflict: $focus" }
    require(compatIds.isEmpty()) { "focus_compat_conflict: $focus" }
}

internal object DevCoreInventoryProbe : DevCompatProbe {
    override val id: String = CORE_INVENTORY_FOCUS

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        StackUpUp.logger?.info(
            "开发自动验收[focused:{}] config expansion={} maxStackSize={} crafting={} rule=minecraft:stick->128",
            id,
            StackUpUpConfig.compat.inventoryExpansion.enabled,
            StackUpUpConfig.maxStackSize,
            StackUpUpConfig.compat.vanilla.craftingSlotLimit,
        )
        check(StackUpUpConfig.maxStackSize >= 128) { "core_inventory_off requires general.maxStackSize>=128" }
        check(!StackUpUpConfig.compat.inventoryExpansion.enabled) { "core_inventory_off requires inventoryExpansion.enabled=false" }
        check(StackUpUpConfig.compat.vanilla.craftingSlotLimit == 64) { "core_inventory_off requires craftingSlotLimit=64" }
        when (val injection = DevRuleInjector.ensureInjected("item = minecraft:stick -> 128")) {
            is DevRuleInjectionResult.Failed -> error("core rule injection failed: ${injection.errors}")
            else -> Unit
        }
        check(sticks(1).maxStackSize == 128) { "core rule not active: ItemStack.maxStackSize=${sticks(1).maxStackSize}" }
        val summaries = ArrayList<String>()
        var failed = false
        fun scenario(name: String, action: () -> String) {
            val result = runCatching(action)
            val summary = "$name: ${result.getOrElse { "FAIL ${formatProbeThrowable(it)}" }}"
            summaries += summary
            if (result.isFailure) {
                failed = true
                StackUpUp.logger?.error("开发自动验收[focused:{}] {}", id, summary)
            } else {
                StackUpUp.logger?.info("开发自动验收[focused:{}] {}", id, summary)
            }
        }
        scenario("player_insert") {
            val player = player(server)
            fillPlayerExceptFirst(player)
            val offered = sticks(129)
            player.inventory.addItemStackToInventory(offered)
            checkCounts("player_insert", 129, player.inventory.getStackInSlot(0).count, offered.count, 128)
        }
        scenario("player_pickup") {
            val player = player(server)
            fillPlayerExceptFirst(player)
            val entity = EntityItem(player.world, player.posX, player.posY, player.posZ, sticks(129))
            entity.setNoPickupDelay()
            entity.onCollideWithPlayer(player)
            checkCounts("player_pickup", 129, player.inventory.getStackInSlot(0).count, if (entity.isDead) 0 else entity.item.count, 128)
        }
        scenario("chest_place") {
            val (player, chest, container) = chest(server)
            player.inventory.itemStack = sticks(129)
            container.slotClick(0, 0, ClickType.PICKUP, player)
            checkCounts("chest_place", 129, chest.getStackInSlot(0).count, player.inventory.itemStack.count, 128)
        }
        scenario("chest_merge") {
            val (player, chest, container) = chest(server)
            player.inventory.itemStack = sticks(64)
            container.slotClick(0, 0, ClickType.PICKUP, player)
            checkCounts("merge_seed", 64, chest.getStackInSlot(0).count, player.inventory.itemStack.count, 64)
            player.inventory.itemStack = sticks(65)
            val before = chest.getStackInSlot(0).count
            container.slotClick(0, 0, ClickType.PICKUP, player)
            checkCounts("chest_merge", 65, chest.getStackInSlot(0).count - before, player.inventory.itemStack.count, 64) +
                " target=${chest.getStackInSlot(0).count}"
        }
        scenario("chest_shift_transfer") {
            val (player, chest, container) = chest(server)
            for (slot in 1 until chest.sizeInventory) chest.setInventorySlotContents(slot, ItemStack(Blocks.STONE, 64))
            player.inventory.itemStack = sticks(128)
            container.slotClick(54, 0, ClickType.PICKUP, player)
            checkCounts("transfer_seed", 128, player.inventory.getStackInSlot(0).count, player.inventory.itemStack.count, 128)
            val before = total(player.inventory) + total(chest) + player.inventory.itemStack.count
            container.slotClick(54, 0, ClickType.QUICK_MOVE, player)
            check(before == total(player.inventory) + total(chest) + player.inventory.itemStack.count) { "transfer total changed" }
            checkCounts("chest_shift_transfer", 128, chest.getStackInSlot(0).count, player.inventory.getStackInSlot(0).count, 128)
        }
        scenario("inv_wrapper") {
            val (_, chest, _) = chest(server)
            val wrapper = InvWrapper(chest)
            val remainder = wrapper.insertItem(0, sticks(129), false)
            val result = checkCounts("inv_wrapper", 129, chest.getStackInSlot(0).count, remainder.count, 128)
            check(wrapper.getSlotLimit(0) >= chest.getStackInSlot(0).count) { "wrapper advertised less than stored" }
            val before = chest.getStackInSlot(0).count
            val extra = wrapper.insertItem(0, sticks(1), false)
            checkCounts("wrapper_saturation", 1, chest.getStackInSlot(0).count - before, extra.count, 0)
            val extracted = wrapper.extractItem(0, 129, false)
            val extraction = checkCounts("wrapper_extract", before, extracted.count, chest.getStackInSlot(0).count, 128)
            "$result advertised=${wrapper.getSlotLimit(0)} targetBeforeExtract=$before; $extraction"
        }
        scenario("armor_bound") {
            val player = player(server)
            val container = player.inventoryContainer
            check(container.getSlot(5).slotStackLimit == 1) { "armor slot capacity is not 1" }
            player.inventory.itemStack = ItemStack(Items.DIAMOND_HELMET, 2)
            container.slotClick(5, 0, ClickType.PICKUP, player)
            checkCounts("armor_bound", 2, container.getSlot(5).stack.count, player.inventory.itemStack.count, 1)
        }
        scenario("armor_rejects_stick") {
            val player = player(server)
            player.inventory.itemStack = sticks(128)
            player.inventoryContainer.slotClick(5, 0, ClickType.PICKUP, player)
            checkCounts("armor_rejects_stick", 128, player.inventoryContainer.getSlot(5).stack.count, player.inventory.itemStack.count, 0)
        }
        scenario("crafting_bound") {
            val player = player(server)
            val container = player.inventoryContainer as ContainerPlayer
            check(container.getSlot(1).slotStackLimit == 64) { "crafting declared capacity changed" }
            player.inventory.itemStack = sticks(128)
            container.slotClick(1, 0, ClickType.PICKUP, player)
            checkCounts("crafting_bound", 128, container.craftMatrix.getStackInSlot(0).count, player.inventory.itemStack.count, 64)
        }
        return DevCompatProbeResult(!failed, summaries.joinToString(" | "))
    }

    private fun player(server: MinecraftServer): EntityPlayerMP {
        val world = server.getWorld(0)
        val player = EntityPlayerMP(server, world, GameProfile(UUID.randomUUID(), "CoreProbe"), PlayerInteractionManager(world))
        net.minecraft.network.NetHandlerPlayServer(
            server,
            net.minecraft.network.NetworkManager(net.minecraft.network.EnumPacketDirection.SERVERBOUND),
            player,
        )
        return player
    }

    private fun chest(server: MinecraftServer): Triple<EntityPlayerMP, TileEntityChest, ContainerChest> {
        val player = player(server)
        val pos = BlockPos(0, 200, 0)
        val world = server.getWorld(0)
        world.setBlockState(pos, Blocks.CHEST.defaultState)
        val chest = world.getTileEntity(pos) as? TileEntityChest ?: error("real chest tile missing at $pos")
        chest.clear()
        return Triple(player, chest, ContainerChest(player.inventory, chest, player))
    }

    private fun fillPlayerExceptFirst(player: EntityPlayerMP) {
        for (slot in 1 until 36) player.inventory.setInventorySlotContents(slot, ItemStack(Blocks.STONE, 64))
        player.inventory.setInventorySlotContents(40, ItemStack(Blocks.STONE, 64))
    }

    private fun sticks(count: Int): ItemStack = ItemStack(Items.STICK, count)

    private fun total(inventory: IInventory): Int = (0 until inventory.sizeInventory).sumOf { inventory.getStackInSlot(it).count }
}

internal fun checkCounts(operation: String, offered: Int, storedDelta: Int, remainder: Int, expectedDelta: Int): String {
    val summary = "$operation offered=$offered storedDelta=$storedDelta remainder/source/cursor=$remainder expectedDelta=$expectedDelta"
    check(storedDelta >= 0 && remainder >= 0 && storedDelta + remainder == offered) { "conservation failure: $summary" }
    check(storedDelta == expectedDelta) { "capacity regression: $summary" }
    return summary
}
