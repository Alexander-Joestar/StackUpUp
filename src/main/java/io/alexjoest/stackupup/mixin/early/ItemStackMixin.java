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

        // 这里只做兜底，不再单独执行规则，避免和 ItemMixin 重复。
        return original;    }
}
