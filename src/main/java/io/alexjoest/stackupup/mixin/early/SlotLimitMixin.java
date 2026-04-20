package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotLimitMixin {
    @Inject(
        method = "getItemStackLimit(Lnet/minecraft/item/ItemStack;)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private void stackupup$applyDynamicSlotLimit(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(StackLimitHooks.resolveDynamicSlotLimit(stack, cir.getReturnValue()));
    }
}
