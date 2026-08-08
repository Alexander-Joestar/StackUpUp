package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityItem.class)
abstract class EntityItemMergeMixin {
    @ModifyExpressionValue(
            method = "combineItems(Lnet/minecraft/entity/item/EntityItem;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;getMaxStackSize()I"
            )
    )
    private int stackupup$useLargestMergeLimit(int original) {
        // original 即 itemstack1.getMaxStackSize()（candidate 的原值），与本实体堆叠上限取大。
        // 非静态 handler 只带 original：receiver(this) 由注入器压栈，与 CommandGiveMixin 同款签名。
        ItemStack current = ((EntityItem) (Object) this).getItem();
        return Math.max(original, current.getMaxStackSize());
    }
}
