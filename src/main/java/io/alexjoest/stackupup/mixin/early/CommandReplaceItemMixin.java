package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.command.CommandReplaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommandReplaceItem.class)
abstract class CommandReplaceItemMixin {
    @ModifyExpressionValue(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/Item;getItemStackLimit()I"
        )
    )
    private int stackupup$expandReplaceItemUpperBound(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
