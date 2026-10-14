package io.alexjoest.stackupup.mixin.late.ic2classic;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "ic2.core.inventory.slots.SlotBase", remap = false)
abstract class SlotBaseMixin {
    private static final int VANILLA_STACK_LIMIT = 64;

    static int keepNativeSlotLimit(int original) {
        return original > VANILLA_STACK_LIMIT ? VANILLA_STACK_LIMIT : original;
    }

    @ModifyReturnValue(method = "func_75219_a()I", at = @At("RETURN"), require = 1)
    private int stackupup$keepNativeWriterLimit(int original) {
        return keepNativeSlotLimit(original);
    }
}
