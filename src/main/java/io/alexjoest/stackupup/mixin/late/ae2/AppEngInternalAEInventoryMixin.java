package io.alexjoest.stackupup.mixin.late.ae2;

import appeng.util.inv.IAEAppEngInventory;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "appeng.tile.inventory.AppEngInternalAEInventory", remap = false)
abstract class AppEngInternalAEInventoryMixin {
    @Shadow @Final private IAEAppEngInventory te;

    @ModifyReturnValue(method = "getSlotLimit(I)I", at = @At("RETURN"), require = 0)
    private int replaceDefaultSlotLimit(int original) {
        if (original != 64
            || !getClass().getName().equals("appeng.tile.inventory.AppEngInternalAEInventory")) {
            return original;
        }
        return StackLimitHooks.getInventoryCompatibilityStackSize(this, this.te, original);
    }
}
