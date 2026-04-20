package io.alexjoest.stackupup.mixin.late;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "appeng.tile.inventory.AppEngInternalAEInventory", remap = false)
abstract class AppEngInternalAEInventoryMixin {
    private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyConstant(method = "<init>*", constant = @Constant(intValue = 64), require = 0)
    private int replaceCompatibilityLimit(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }

    @ModifyReturnValue(method = "getInventoryStackLimit", at = @At("RETURN"), require = 0)
    private int stackupup$expandReturnedLimit(int original) {
        if (original == VANILLA_STACK_LIMIT) {
            return StackLimitHooks.getCompatibilityStackSize();
        }
        return original;
    }
}
