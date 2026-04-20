package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(
        method = "getMaxStackSize()I",
        at = @At("RETURN"),
        cancellable = true
    )
    private void stackupup$applyRules(CallbackInfoReturnable<Integer> cir) {
        final ItemStack stack = (ItemStack) (Object) this;

        // 1.12.2 下这里是 ItemStack 自身的返回路径，仍需单独接入统一规则内核。
        // ItemMixin 继续覆盖外部直接调用 Item#getItemStackLimit(ItemStack) 的路径。
        cir.setReturnValue(StackLimitHooks.applyDynamicStackLimit(stack, cir.getReturnValue()));
    }
}
