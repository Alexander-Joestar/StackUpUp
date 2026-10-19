package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemStackHandler.class, remap = false)
public abstract class ForgeItemHandlerLimitMixin {

    @ModifyReturnValue(method = "getSlotLimit", at = @At("RETURN"))
    private int stackupup$replaceCompatibilityLimit(int original, int slot) {
        return StackLimitHooks.getInventoryCompatibilityStackSize(this, original);
    }
}
