package io.alexjoest.stackupup.mixin.late;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.common.util.inventory.IEInventoryHandler", remap = false)
abstract class IEInventoryHandlerMixin {
    @Unique
    private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyReturnValue(method = "getSlotLimit", at = @At("RETURN"), require = 0)
    private int stackupup$expandSlotLimit(int original) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }

    @ModifyExpressionValue(
        method = "insertItem",
        at = @At(
            value = "INVOKE",
            target = "Lblusunrize/immersiveengineering/common/util/inventory/IIEInventory;getSlotLimit(I)I"
        ),
        require = 0
    )
    private int stackupup$expandInsertSlotLimit(int original) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }
}
