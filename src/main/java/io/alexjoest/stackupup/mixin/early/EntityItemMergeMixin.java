package io.alexjoest.stackupup.mixin.early;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityItem.class)
abstract class EntityItemMergeMixin {
    @Redirect(
            method = "combineItems(Lnet/minecraft/entity/item/EntityItem;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;getMaxStackSize()I"
            )
    )
    private int stackupup$useLargestMergeLimit(ItemStack candidate, EntityItem other) {
        ItemStack current = ((EntityItem) (Object) this).getItem();
        return Math.max(candidate.getMaxStackSize(), current.getMaxStackSize());
    }
}
