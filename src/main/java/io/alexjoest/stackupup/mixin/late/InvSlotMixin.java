package io.alexjoest.stackupup.mixin.late;

import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "ic2.core.block.invslot.InvSlot", remap = false)
abstract class InvSlotMixin {
    @ModifyConstant(method = "<init>*", constant = @Constant(intValue = 64), require = 0)
    private static int replaceCompatibilityLimit(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
