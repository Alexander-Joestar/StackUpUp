package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.RangedWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    value = {
        ItemStackHandler.class,
        EntityEquipmentInvWrapper.class,
        InvWrapper.class,
        SidedInvWrapper.class,
        CombinedInvWrapper.class,
        RangedWrapper.class
    },
    remap = false
)
public abstract class ForgeItemHandlerLimitMixin {
    @Unique private static final int VANILLA_STACK_LIMIT = 64;

    @Inject(method = "getSlotLimit", at = @At("RETURN"), cancellable = true)
    private void stackupup$replaceCompatibilityLimit(int slot, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() == VANILLA_STACK_LIMIT) {
            cir.setReturnValue(StackLimitHooks.getCompatibilityStackSize());
        }
    }
}
