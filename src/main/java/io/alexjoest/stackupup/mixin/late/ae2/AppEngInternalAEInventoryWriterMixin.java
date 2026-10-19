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

/**
 * Keeps AE2's AE-inventory metadata ({@code IAEItemStack#setStackSize}) in sync with the
 * {@code ItemStack} that {@code insertItem}/{@code extractItem} changed, so the write path cannot
 * advertise a stack size the backing array does not have.
 *
 * <p><b>Why every {@code @At} carries two targets:</b> the wrapped call is a *Minecraft member*
 * ({@code ItemStack#grow(int)}) inside a reobfuscated mod class, so the release jar calls it as
 * {@code ItemStack.func_190917_f:(I)V} (verified with {@code javap -c} on
 * {@code ae2-uel-v0.56.7.jar} for both {@code insertItem} and {@code extractItem}); the dev
 * environment's SRG&rarr;MCP deobfuscation presents it as {@code grow(I)V}. {@code remap = false}
 * consults no refmap, so both targets are declared and exactly one matches per environment. With the
 * MCP target alone both hooks were silently inert in production (no {@code require}, so a zero match
 * is not an error) and the AE metadata was never written back.
 */
@Pseudo
@Mixin(targets = "appeng.tile.inventory.AppEngInternalAEInventory", remap = false)
abstract class AppEngInternalAEInventoryWriterMixin {
    @Shadow @Final private IAEItemStack[] inv;

    @WrapOperation(
        method = "insertItem(ILnet/minecraft/item/ItemStack;Z)Lnet/minecraft/item/ItemStack;",
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;func_190917_f(I)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;grow(I)V")
        }
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
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;func_190917_f(I)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;grow(I)V")
        }
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
