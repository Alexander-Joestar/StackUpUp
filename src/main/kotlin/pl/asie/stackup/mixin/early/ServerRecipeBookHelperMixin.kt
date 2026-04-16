package pl.asie.stackup.mixin.early

import net.minecraft.util.ServerRecipeBookHelper
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.Constant
import org.spongepowered.asm.mixin.injection.ModifyConstant
import pl.asie.stackup.StackUpHelpers

@Mixin(ServerRecipeBookHelper::class)
abstract class ServerRecipeBookHelperMixin {
    @ModifyConstant(
        method = ["func_194324_a"],
        constant = [Constant(intValue = 64)]
    )
    private fun replaceRecipeBookLimit(original: Int): Int = StackUpHelpers.getMaxStackSize()
}
