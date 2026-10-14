package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.command.CommandGive;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommandGive.class)
abstract class CommandGiveMixin {
    @ModifyExpressionValue(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/Item;getItemStackLimit()I"
        )
    )
    private int stackupup$expandGiveUpperBound(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
