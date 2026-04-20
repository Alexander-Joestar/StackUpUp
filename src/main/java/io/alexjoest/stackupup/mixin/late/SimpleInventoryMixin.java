package io.alexjoest.stackupup.mixin.late;

import io.alexjoest.stackupup.StackLimitHooks;
import org.cyclops.cyclopscore.inventory.SimpleInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SimpleInventory.class, remap = false)
abstract class SimpleInventoryMixin {
    private static final int VANILLA_STACK_LIMIT = 64;

    @Inject(method = "getInventoryStackLimit", at = @At("RETURN"), cancellable = true)
    private void stackupupExpandDefaultInventoryLimit(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() == VANILLA_STACK_LIMIT) {
            cir.setReturnValue(StackLimitHooks.getCompatibilityStackSize());
        }
    }
}
