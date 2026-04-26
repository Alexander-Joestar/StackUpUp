package io.alexjoest.stackupup.mixin.late;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.brandon3055.brandonscore.blocks.TileInventoryBase", remap = false)
abstract class BrandonsCoreInventoryLimitMixin {
    @Unique
    private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyReturnValue(method = "getInventoryStackLimit", at = @At("RETURN"), require = 0)
    private int stackupup$expandDefaultInventoryLimit(int original) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }
}
