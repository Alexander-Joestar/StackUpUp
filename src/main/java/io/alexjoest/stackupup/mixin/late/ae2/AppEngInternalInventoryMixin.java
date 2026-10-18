package io.alexjoest.stackupup.mixin.late.ae2;

import appeng.util.inv.IAEAppEngInventory;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "appeng.tile.inventory.AppEngInternalInventory", remap = false)
abstract class AppEngInternalInventoryMixin {

    @Shadow
    public abstract IAEAppEngInventory getTileEntity();

    @ModifyReturnValue(method = "getSlotLimit(I)I", at = @At("RETURN"), require = 0)
    private int replaceDefaultSlotLimit(int original) {
        if (original != 64
            || !getClass().getName().equals("appeng.tile.inventory.AppEngInternalInventory")) {
            return original;
        }
        return StackLimitHooks.getInventoryCompatibilityStackSize(this, this.getTileEntity(), original);
    }
}
