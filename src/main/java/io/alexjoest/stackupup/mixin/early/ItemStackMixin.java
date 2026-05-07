package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @ModifyReturnValue(
            method = "getMaxStackSize()I",
            at = @At("RETURN")
    )
    private int stackupup$applyRules(int original) {
        final ItemStack stack = (ItemStack) (Object) this;

        if (StackLimitHooks.shouldBypassDynamicItemRules()) {
            return original;
        }

        // ItemMixin 已处理时跳过，否则 GT 等不走 Item#getItemStackLimit 的 mod 到这里补齐。
        if (StackLimitHooks.shouldSkipNestedItemStackLimit(stack, original)) {
            return original;
        }
        return StackLimitHooks.applyDynamicStackLimit(stack, original);
    }
}