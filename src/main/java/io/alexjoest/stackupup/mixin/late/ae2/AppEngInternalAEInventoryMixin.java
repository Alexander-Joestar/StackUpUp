package io.alexjoest.stackupup.mixin.late.ae2;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "appeng.tile.inventory.AppEngInternalAEInventory", remap = false)
abstract class AppEngInternalAEInventoryMixin {
    private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyConstant(method = "<init>*", constant = @Constant(intValue = 64), require = 0)
    private static int replaceCompatibilityLimit(int original) {
        return StackLimitHooks.getInventoryCompatibilityStackSize(original);
    }

    @ModifyReturnValue(method = "getSlotLimit(I)I", at = @At("RETURN"), require = 0)
    private int replaceDefaultSlotLimit(int original) {
        if (original != VANILLA_STACK_LIMIT
            || !getClass().getName().equals("appeng.tile.inventory.AppEngInternalAEInventory")) {
            return original;
        }
        return StackLimitHooks.getInventoryCompatibilityStackSize(original);
    }
}
