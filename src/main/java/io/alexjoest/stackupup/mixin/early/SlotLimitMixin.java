package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Slot.class)
public abstract class SlotLimitMixin {
    @ModifyReturnValue(
        method = "getItemStackLimit(Lnet/minecraft/item/ItemStack;)I",
        at = @At("RETURN")
    )
    private int stackupup$applyDynamicSlotLimit(int original, ItemStack stack) {
        return StackLimitHooks.resolveDynamicSlotLimit(stack, original);
    }
}
