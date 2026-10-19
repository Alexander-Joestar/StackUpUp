package io.alexjoest.stackupup.mixin.late.ic2classic;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.compat.ic2classic.SlotBaseNativeLimit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "ic2.core.inventory.slots.SlotBase", remap = false)
abstract class SlotBaseMixin {
    @ModifyReturnValue(
            method = {"func_75219_a()I", "getSlotStackLimit()I"},
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private int stackupup$keepNativeWriterLimit(int original) {
        return SlotBaseNativeLimit.clamp(original);
    }
}
