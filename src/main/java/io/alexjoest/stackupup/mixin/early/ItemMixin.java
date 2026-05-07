package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Item.class)
public abstract class ItemMixin {
    @ModifyReturnValue(
            method = "getItemStackLimit(Lnet/minecraft/item/ItemStack;)I",
            remap = false,
            at = @At("RETURN")
    )
    private int stackupup$applyRules(int original, ItemStack stack) {
        if (StackLimitHooks.shouldBypassDynamicItemRules()) {
            return original;
        }

        int resolved = StackLimitHooks.applyDynamicStackLimit(stack, original);
        return StackLimitHooks.markResolvedItemLimit(stack, resolved);
    }
}
