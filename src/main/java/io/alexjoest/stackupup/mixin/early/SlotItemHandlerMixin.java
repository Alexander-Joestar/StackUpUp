package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SlotItemHandler.class, remap = false)
public abstract class SlotItemHandlerMixin {
    @Unique private static final int VANILLA_STACK_LIMIT = 64;

    @Shadow
    public abstract int getSlotStackLimit();

    @ModifyReturnValue(method = "getSlotStackLimit", at = @At("RETURN"))
    private int stackupup$replaceCompatibilityLimit(int original) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }

    @ModifyReturnValue(method = "getItemStackLimit", at = @At("RETURN"))
    private int stackupup$resolveItemAwareLimit(int original, ItemStack stack) {
        return StackLimitHooks.resolveItemHandlerSlotLimit(
            stack,
            original,
            this.getSlotStackLimit()
        );
    }
}
