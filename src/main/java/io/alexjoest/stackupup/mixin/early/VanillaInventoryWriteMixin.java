package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryMerchant;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityLockableLoot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({
    TileEntityLockableLoot.class,
    TileEntityFurnace.class,
    TileEntityBrewingStand.class,
    TileEntityHopper.class,
    EntityMinecartContainer.class,
    InventoryPlayer.class,
    InventoryBasic.class,
    InventoryMerchant.class,
    InventoryCrafting.class,
    InventoryCraftResult.class
})
abstract class VanillaInventoryWriteMixin {
    @Inject(
        method = "setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V",
        at = @At("HEAD"),
        require = 0
    )
    private void stackupup$beginInventoryWrite(int index, ItemStack stack, CallbackInfo ci) {
        StackLimitHooks.beginInventoryWrite(stack);
    }

    @Inject(
        method = "setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V",
        at = @At("RETURN"),
        require = 0
    )
    private void stackupup$endInventoryWrite(int index, ItemStack stack, CallbackInfo ci) {
        StackLimitHooks.endInventoryWrite();
    }
}
