package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.inventory.InventoryMerchant;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityShulkerBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({
    TileEntityDispenser.class,
    TileEntityChest.class,
    TileEntityFurnace.class,
    TileEntityBrewingStand.class,
    TileEntityHopper.class,
    TileEntityShulkerBox.class,
    EntityMinecartContainer.class,
    InventoryPlayer.class,
    InventoryBasic.class,
    InventoryLargeChest.class,
    InventoryMerchant.class,
    InventoryCrafting.class,
    InventoryCraftResult.class
})
abstract class VanillaInventoryLimitMixin {
    @Unique
    private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyReturnValue(
        method = "getInventoryStackLimit()I",
        at = @At("RETURN"),
        require = 0
    )
    private int stackupup$replaceCompatibilityLimit(int original) {
        int limit = StackLimitHooks.resolveInventoryWriteLimit(original);
        if (limit != original) {
            return limit;
        }

        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }
}
