package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.core.InventoryHelperPerformanceSplice;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryHelper.class)
public abstract class InventoryHelperMixin {
    @Inject(
        method = "spawnItemStack(Lnet/minecraft/world/World;DDDLnet/minecraft/item/ItemStack;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void useLargeStackSplit(World worldIn, double x, double y, double z, ItemStack stack, CallbackInfo ci) {
        InventoryHelperPerformanceSplice.spawnItemStack(worldIn, x, y, z, stack);
        ci.cancel();
    }
}
