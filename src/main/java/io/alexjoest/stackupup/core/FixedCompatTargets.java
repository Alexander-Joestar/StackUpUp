package io.alexjoest.stackupup.core;

/**
 * 这些目标已经迁入显式 mixin，不应再走动态 ASM 兜底。
 * 现在用纯 Java 编写，避免 Kotlin 集合工厂在 coremod 早期拉取 stdlib。
 */
public final class FixedCompatTargets {

    private FixedCompatTargets() {}

    private static class Entry {
        final String className;
        final boolean probeCovered;

        Entry(String className, boolean probeCovered) {
            this.className = className;
            this.probeCovered = probeCovered;
        }
    }

    private static Entry e(String className) {
        return new Entry(className, false);
    }

    // probe 覆盖目标（DynamicCompatMethodProbe 已接管）固定携带 probeCovered=true。
    private static Entry eProbeCovered(String className) {
        return new Entry(className, true);
    }

    private static final Entry[] ENTRIES = {
        // 原版 IInventory / 容器实现
        e("net.minecraft.tileentity.TileEntityDispenser"),
        e("net.minecraft.tileentity.TileEntityChest"),
        e("net.minecraft.tileentity.TileEntityFurnace"),
        e("net.minecraft.tileentity.TileEntityBrewingStand"),
        e("net.minecraft.tileentity.TileEntityHopper"),
        e("net.minecraft.tileentity.TileEntityShulkerBox"),
        e("net.minecraft.entity.item.EntityMinecartContainer"),
        e("net.minecraft.entity.player.InventoryPlayer"),
        e("net.minecraft.inventory.InventoryBasic"),
        e("net.minecraft.inventory.InventoryEnderChest"),
        e("net.minecraft.inventory.InventoryLargeChest"),
        e("net.minecraft.inventory.InventoryMerchant"),
        e("net.minecraft.inventory.InventoryCrafting"),
        e("net.minecraft.inventory.InventoryCraftResult"),

        // 已由 late mixin 独占负责的第三方库存
        e("appeng.tile.inventory.AppEngInternalInventory"),
        e("appeng.tile.inventory.AppEngInternalAEInventory"),
        e("ae2.util.inv.AppEngInternalInventory"),
        e("ae2.api.inventories.InternalInventory"),
        e("ae2.api.inventories.ContainerAdapter"),
        eProbeCovered("org.cyclops.cyclopscore.inventory.SimpleInventory"),

        // Forge item handler / wrapper
        eProbeCovered("net.minecraftforge.items.SlotItemHandler"),
        e("net.minecraftforge.items.ItemStackHandler"),
        e("net.minecraftforge.items.VanillaDoubleChestItemHandler"),
        e("net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper"),
        e("net.minecraftforge.items.wrapper.EmptyHandler"),
        eProbeCovered("net.minecraftforge.items.wrapper.InvWrapper"),
        eProbeCovered("net.minecraftforge.items.wrapper.SidedInvWrapper"),
        eProbeCovered("net.minecraftforge.items.wrapper.CombinedInvWrapper"),
        eProbeCovered("net.minecraftforge.items.wrapper.RangedWrapper"),
    };

    private static final String[] ALL_TARGETS = copyNames(false);
    private static final String[] PROBE_TARGETS = copyNames(true);

    public static boolean contains(String className) {
        for (Entry entry : ENTRIES) {
            if (entry.className.equals(className)) {
                return true;
            }
        }
        return false;
    }

    public static String[] all() {
        return ALL_TARGETS.clone();
    }

    public static String[] probeTargets() {
        return PROBE_TARGETS.clone();
    }

    private static String[] copyNames(boolean probeCoveredOnly) {
        int count = 0;
        for (Entry entry : ENTRIES) {
            if (!probeCoveredOnly || entry.probeCovered) {
                count++;
            }
        }
        String[] result = new String[count];
        int index = 0;
        for (Entry entry : ENTRIES) {
            if (!probeCoveredOnly || entry.probeCovered) {
                result[index++] = entry.className;
            }
        }
        return result;
    }
}
