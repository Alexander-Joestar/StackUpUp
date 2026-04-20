package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.command.CommandReplaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CommandReplaceItem.class)
abstract class CommandReplaceItemMixin {
    @ModifyArg(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/command/CommandBase;parseInt(Ljava/lang/String;II)I"
        ),
        index = 2
    )
    private int expandReplaceItemUpperBound(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
