package pl.asie.stackup.mixin.early

import net.minecraft.client.renderer.entity.RenderEntityItem
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Constant
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyConstant
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import pl.asie.stackup.client.StackUpClientHelpers

@Mixin(RenderEntityItem::class)
abstract class RenderEntityItemMixin {
    @Inject(method = ["getModelCount", "func_177078_a"], at = [At("HEAD")], cancellable = true)
    private fun useDynamicModelCount(stack: ItemStack, cir: CallbackInfoReturnable<Int>) {
        cir.returnValue = StackUpClientHelpers.getModelCount(stack)
    }

    @ModifyConstant(
        method = ["doRender", "func_76986_a"],
        constant = [Constant(floatValue = -0.09375f)]
    )
    private fun replaceNegativeDistance(original: Float, entity: EntityItem): Float {
        return StackUpClientHelpers.getItemRenderDistanceNeg(entity)
    }

    @ModifyConstant(
        method = ["doRender", "func_76986_a"],
        constant = [Constant(floatValue = 0.09375f)]
    )
    private fun replacePositiveDistance(original: Float, entity: EntityItem): Float {
        return StackUpClientHelpers.getItemRenderDistance(entity)
    }
}
