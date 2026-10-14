package io.alexjoest.stackupup.mixin.late.ae2supergiant;

import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "ae2.util.inv.AppEngInternalInventory", remap = false)
abstract class AppEngInternalInventoryMixin {
    @ModifyConstant(method = "<init>*", constant = @Constant(intValue = 64), require = 0)
    private static int replaceCompatibilityLimit(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
