package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.RangedWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    value = {
        ItemStackHandler.class,
        EntityEquipmentInvWrapper.class,
        InvWrapper.class,
        SidedInvWrapper.class,
        CombinedInvWrapper.class,
        RangedWrapper.class
    },
    remap = false
)
public abstract class ForgeItemHandlerLimitMixin {
    @Unique private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyReturnValue(method = "getSlotLimit", at = @At("RETURN"))
    private int stackupup$replaceCompatibilityLimit(int original, int slot) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }
}
