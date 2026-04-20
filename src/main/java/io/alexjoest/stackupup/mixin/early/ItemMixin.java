package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemMixin {
    @Inject(
        method = "getItemStackLimit(Lnet/minecraft/item/ItemStack;)I",
        remap = false,
        at = @At("RETURN"),
        cancellable = true
    )
    private void stackupup$applyRules(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(StackLimitHooks.applyDynamicStackLimit(stack, cir.getReturnValue()));
    }
}
