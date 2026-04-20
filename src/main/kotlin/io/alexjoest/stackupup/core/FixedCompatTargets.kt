package io.alexjoest.stackupup.core

/**
 * 这些目标已经迁入显式 mixin，不应再走动态 ASM 兜底。
 * 这里避免使用 Kotlin 集合工厂，防止 coremod 早期再次拉起 stdlib。
 */
internal object FixedCompatTargets {
    private val names = arrayOf(
        // 原版 IInventory / 容器实现
        "net.minecraft.tileentity.TileEntityDispenser",
        "net.minecraft.tileentity.TileEntityChest",
        "net.minecraft.tileentity.TileEntityFurnace",
        "net.minecraft.tileentity.TileEntityBrewingStand",
        "net.minecraft.tileentity.TileEntityHopper",
        "net.minecraft.tileentity.TileEntityShulkerBox",
        "net.minecraft.entity.item.EntityMinecartContainer",
        "net.minecraft.entity.player.InventoryPlayer",
        "net.minecraft.inventory.InventoryBasic",
        "net.minecraft.inventory.InventoryEnderChest",
        "net.minecraft.inventory.InventoryLargeChest",
        "net.minecraft.inventory.InventoryMerchant",
        "net.minecraft.inventory.InventoryCrafting",
        "net.minecraft.inventory.InventoryCraftResult",

        // 已由 late mixin 独占负责的第三方库存
        "appeng.tile.inventory.AppEngInternalInventory",
        "appeng.tile.inventory.AppEngInternalAEInventory",
        "org.cyclops.cyclopscore.inventory.SimpleInventory",

        // Forge item handler / wrapper
        "net.minecraftforge.items.SlotItemHandler",
        "net.minecraftforge.items.ItemStackHandler",
        "net.minecraftforge.items.VanillaDoubleChestItemHandler",
        "net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper",
        "net.minecraftforge.items.wrapper.EmptyHandler",
        "net.minecraftforge.items.wrapper.InvWrapper",
        "net.minecraftforge.items.wrapper.SidedInvWrapper",
        "net.minecraftforge.items.wrapper.CombinedInvWrapper",
        "net.minecraftforge.items.wrapper.RangedWrapper"
    )

    private val probeTargets = arrayOf(
        "org.cyclops.cyclopscore.inventory.SimpleInventory",
        "net.minecraftforge.items.SlotItemHandler",
        "net.minecraftforge.items.wrapper.InvWrapper",
        "net.minecraftforge.items.wrapper.SidedInvWrapper",
        "net.minecraftforge.items.wrapper.CombinedInvWrapper",
        "net.minecraftforge.items.wrapper.RangedWrapper"
    )

    fun contains(className: String): Boolean {
        for (name in names) {
            if (name == className) {
                return true
            }
        }
        return false
    }

    fun all(): Array<String> = names.copyOf()

    fun probeTargets(): Array<String> = probeTargets.copyOf()
}
