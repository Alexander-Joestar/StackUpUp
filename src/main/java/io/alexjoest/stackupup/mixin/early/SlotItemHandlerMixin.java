package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SlotItemHandler.class, remap = false)
public abstract class SlotItemHandlerMixin {
    @Unique private static final int VANILLA_STACK_LIMIT = 64;

    @Shadow
    public abstract int getSlotStackLimit();

    @Inject(method = "getSlotStackLimit", at = @At("RETURN"), cancellable = true)
    private void stackupup$replaceCompatibilityLimit(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() == VANILLA_STACK_LIMIT) {
            cir.setReturnValue(StackLimitHooks.getCompatibilityStackSize());
        }
    }

    @Inject(method = "getItemStackLimit", at = @At("RETURN"), cancellable = true)
    private void stackupup$resolveItemAwareLimit(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(
            StackLimitHooks.resolveItemHandlerSlotLimit(
                stack,
                cir.getReturnValue(),
                this.getSlotStackLimit()
            )
        );
    }
}
