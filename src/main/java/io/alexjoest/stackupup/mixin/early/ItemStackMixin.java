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

        // 普通物品会先经过 Item#getItemStackLimit(ItemStack) 的动态规则入口；
        // 这里仅在“物品类自己覆写了上限逻辑、绕开 ItemMixin”时补一次规则，
        // 同时用线程本地标记避免把 + - * / 再执行第二遍。
        if (StackLimitHooks.shouldSkipNestedItemStackLimit(stack, original)) {
            return original;
        }
        return StackLimitHooks.applyDynamicStackLimit(stack, original);
    }
}
