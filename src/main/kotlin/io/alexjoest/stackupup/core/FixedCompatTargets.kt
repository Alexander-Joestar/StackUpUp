package io.alexjoest.stackupup.core

/**
 * 这些目标已经迁入显式 mixin，不应再走动态 ASM 兜底。
 * 这里避免使用 Kotlin 集合工厂，防止 coremod 早期再次拉起 stdlib。
 */
internal object FixedCompatTargets {
    private class Entry(val className: String, val probeCovered: Boolean)

    private val entries = arrayOf(
        // 原版 IInventory / 容器实现
        entry("net.minecraft.tileentity.TileEntityDispenser"),
        entry("net.minecraft.tileentity.TileEntityChest"),
        entry("net.minecraft.tileentity.TileEntityFurnace"),
        entry("net.minecraft.tileentity.TileEntityBrewingStand"),
        entry("net.minecraft.tileentity.TileEntityHopper"),
        entry("net.minecraft.tileentity.TileEntityShulkerBox"),
        entry("net.minecraft.entity.item.EntityMinecartContainer"),
        entry("net.minecraft.entity.player.InventoryPlayer"),
        entry("net.minecraft.inventory.InventoryBasic"),
        entry("net.minecraft.inventory.InventoryEnderChest"),
        entry("net.minecraft.inventory.InventoryLargeChest"),
        entry("net.minecraft.inventory.InventoryMerchant"),
        entry("net.minecraft.inventory.InventoryCrafting"),
        entry("net.minecraft.inventory.InventoryCraftResult"),

        // 已由 late mixin 独占负责的第三方库存
        entry("appeng.tile.inventory.AppEngInternalInventory"),
        entry("appeng.tile.inventory.AppEngInternalAEInventory"),
        entry("org.cyclops.cyclopscore.inventory.SimpleInventory", probeCovered = true),

        // Forge item handler / wrapper
        entry("net.minecraftforge.items.SlotItemHandler", probeCovered = true),
        entry("net.minecraftforge.items.ItemStackHandler"),
        entry("net.minecraftforge.items.VanillaDoubleChestItemHandler"),
        entry("net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper"),
        entry("net.minecraftforge.items.wrapper.EmptyHandler"),
        entry("net.minecraftforge.items.wrapper.InvWrapper", probeCovered = true),
        entry("net.minecraftforge.items.wrapper.SidedInvWrapper", probeCovered = true),
        entry("net.minecraftforge.items.wrapper.CombinedInvWrapper", probeCovered = true),
        entry("net.minecraftforge.items.wrapper.RangedWrapper", probeCovered = true)
    )

    private val allTargets = copyNames(entries.size, includeProbeCoveredOnly = false)
    private val probeTargets = copyNames(countProbeTargets(), includeProbeCoveredOnly = true)

    fun contains(className: String): Boolean {
        for (entry in entries) {
            if (entry.className == className) {
                return true
            }
        }
        return false
    }

    fun all(): Array<String> = allTargets.copyOf()

    fun probeTargets(): Array<String> = probeTargets.copyOf()

    private fun countProbeTargets(): Int {
        var count = 0
        for (entry in entries) {
            if (entry.probeCovered) {
                count++
            }
        }
        return count
    }

    private fun copyNames(size: Int, includeProbeCoveredOnly: Boolean): Array<String> {
        val selected = arrayOfNulls<String>(size)
        var index = 0
        for (entry in entries) {
            if (includeProbeCoveredOnly && !entry.probeCovered) {
                continue
            }
            selected[index++] = entry.className
        }
        @Suppress("UNCHECKED_CAST")
        return selected as Array<String>
    }

    private fun entry(className: String, probeCovered: Boolean = false): Entry =
        Entry(className = className, probeCovered = probeCovered)
}
