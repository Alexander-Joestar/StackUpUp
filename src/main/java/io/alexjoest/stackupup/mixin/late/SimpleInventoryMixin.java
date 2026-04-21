package io.alexjoest.stackupup.mixin.late;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.cyclops.cyclopscore.inventory.SimpleInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SimpleInventory.class, remap = false)
abstract class SimpleInventoryMixin {
    private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyReturnValue(method = "getInventoryStackLimit", at = @At("RETURN"))
    private int stackupup$expandDefaultInventoryLimit(int original) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }
}
