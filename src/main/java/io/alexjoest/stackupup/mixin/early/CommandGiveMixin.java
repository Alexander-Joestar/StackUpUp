package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.command.CommandGive;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CommandGive.class)
abstract class CommandGiveMixin {
    @ModifyArg(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/command/CommandBase;parseInt(Ljava/lang/String;II)I"
        ),
        index = 2
    )
    private int expandGiveUpperBound(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
