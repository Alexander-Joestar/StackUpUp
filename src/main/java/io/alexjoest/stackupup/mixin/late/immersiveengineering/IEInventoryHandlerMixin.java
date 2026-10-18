package io.alexjoest.stackupup.mixin.late.immersiveengineering;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Capacity mixin for IE's item-handler insert path only.
 *
 * <p>{@code IEInventoryHandler} is a concrete {@code IItemHandlerModifiable} whose
 * {@code getSlotLimit(int)} returns a literal 64, so {@code InventoryStackLimitTransformer} already
 * rewrites that getter at class load; the {@code getSlotLimit} handler that used to sit here was
 * deleted as redundant. The remaining handler wraps {@code insertItem}'s call to the owning tile's
 * {@code IIEInventory#getSlotLimit}, which resolves to IE tile implementations rather than to this
 * handler class and therefore stays outside the transformer's hierarchy coverage.</p>
 */
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.common.util.inventory.IEInventoryHandler", remap = false)
abstract class IEInventoryHandlerMixin {

    @ModifyExpressionValue(
        method = "insertItem",
        at = @At(
            value = "INVOKE",
            target = "Lblusunrize/immersiveengineering/common/util/inventory/IIEInventory;getSlotLimit(I)I"
        ),
        require = 0
    )
    private int stackupup$expandInsertSlotLimit(int original) {
        return StackLimitHooks.getInventoryCompatibilityStackSize(this, original);
    }
}
