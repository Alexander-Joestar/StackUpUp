package io.alexjoest.stackupup.mixin.late.ae2;

import appeng.api.storage.data.IAEItemStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "appeng.tile.inventory.AppEngInternalAEInventory", remap = false)
abstract class AppEngInternalAEInventoryWriterMixin {
    @Shadow @Final private IAEItemStack[] inv;

    @WrapOperation(
        method = "insertItem(ILnet/minecraft/item/ItemStack;Z)Lnet/minecraft/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;grow(I)V")
    )
    private void writeBackInsertedStack(
        ItemStack instance,
        int amount,
        Operation<Void> original,
        int slot,
        ItemStack stack,
        boolean simulate
    ) {
        original.call(instance, amount);
        this.inv[slot].setStackSize(instance.getCount());
    }

    @WrapOperation(
        method = "extractItem(IIZ)Lnet/minecraft/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;grow(I)V")
    )
    private void writeBackExtractedStack(
        ItemStack instance,
        int amount,
        Operation<Void> original,
        int slot,
        int requestedAmount,
        boolean simulate
    ) {
        original.call(instance, amount);
        this.inv[slot].setStackSize(instance.getCount());
    }
}
