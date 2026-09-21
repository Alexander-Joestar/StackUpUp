package io.alexjoest.stackupup.mixin.late.nuclearcraft;

import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(
    targets = {
        "nc.tile.inventory.TileInventory",
        "nc.tile.fluid.TileFluidInventory",
        "nc.tile.energy.TileEnergyInventory",
        "nc.tile.energyFluid.TileEnergyFluidInventory",
        "nc.tile.processor.TileNuclearFurnace"
    },
    remap = false
)
abstract class NuclearCraftTileInventoryLimitMixin {
    public int getInventoryStackLimit() {
        return StackLimitHooks.getCompatibilityStackSize();
    }

    public int func_70297_j_() {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
