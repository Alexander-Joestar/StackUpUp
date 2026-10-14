package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.util.ServerRecipeBookHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ServerRecipeBookHelper.class)
abstract class ServerRecipeBookHelperMixin {
    @ModifyConstant(
        method = "func_194324_a",
        constant = @Constant(intValue = 64)
    )
    private int replaceRecipeBookLimit(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
