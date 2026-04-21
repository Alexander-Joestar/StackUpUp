package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.client.StackRenderHooks;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(RenderEntityItem.class)
abstract class RenderEntityItemMixin {
    @ModifyReturnValue(
        method = "getModelCount(Lnet/minecraft/item/ItemStack;)I",
        at = @At("RETURN")
    )
    private int stackupup$useDynamicModelCount(int original, ItemStack stack) {
        return StackRenderHooks.getModelCount(stack);
    }

    @ModifyConstant(
        method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        constant = @Constant(floatValue = -0.09375F)
    )
    private float replaceNegativeDistance(float original, EntityItem entity) {
        return StackRenderHooks.getItemRenderDistanceNeg(entity);
    }

    @ModifyConstant(
        method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        constant = @Constant(floatValue = 0.09375F)
    )
    private float replacePositiveDistance(float original, EntityItem entity) {
        return StackRenderHooks.getItemRenderDistance(entity);
    }
}
