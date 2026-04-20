package io.alexjoest.stackupup.mixin.early;

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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
    private static final int VANILLA_STACK_LIMIT = 64;

    @Inject(
        method = "getInventoryStackLimit()I",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void replaceCompatibilityLimit(CallbackInfoReturnable<Integer> cir) {
        int limit = StackLimitHooks.resolveInventoryWriteLimit(cir.getReturnValue());
        if (limit != cir.getReturnValue()) {
            cir.setReturnValue(limit);
            return;
        }

        if (cir.getReturnValue() == VANILLA_STACK_LIMIT) {
            cir.setReturnValue(StackLimitHooks.getCompatibilityStackSize());
        }
    }
}
