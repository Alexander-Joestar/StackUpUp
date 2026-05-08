package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.alexjoest.stackupup.ContainerInsertHooks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Container.class)
public abstract class ContainerMixin {
    @WrapOperation(
        method = "mergeItemStack(Lnet/minecraft/item/ItemStack;IIZ)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/inventory/Slot;getSlotStackLimit()I"
        )
    )
    private int stackupup$useItemAwareMergeLimit(
        Slot slot, Operation<Integer> original,
        ItemStack stack, int startIndex, int endIndex, boolean reverseDirection
    ) {
        return ContainerInsertHooks.resolveMergeSlotLimit(slot, stack, original.call(slot));
    }
}
