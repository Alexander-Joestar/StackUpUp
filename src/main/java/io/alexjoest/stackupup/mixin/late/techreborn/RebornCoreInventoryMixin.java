package io.alexjoest.stackupup.mixin.late.techreborn;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "reborncore.common.util.Inventory", remap = false)
abstract class RebornCoreInventoryMixin {
    @ModifyReturnValue(method = {"getInventoryStackLimit()I", "func_70297_j_()I"}, at = @At("RETURN"), require = 0)
    private static int stackupup$expandInventoryStackLimit(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
