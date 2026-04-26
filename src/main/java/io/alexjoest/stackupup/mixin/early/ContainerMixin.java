package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.alexjoest.stackupup.ContainerInsertHooks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.entity.player.EntityPlayer;
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
        Slot slot,
        Operation<Integer> original,
        ItemStack stack,
        int startIndex,
        int endIndex,
        boolean reverseDirection
    ) {
        return slot.getItemStackLimit(stack);
    }

    @WrapOperation(
        method = "mergeItemStack(Lnet/minecraft/item/ItemStack;IIZ)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V"
        )
    )
    private void stackupup$restoreRemainderAfterMergePut(
        Slot slot,
        ItemStack attemptedStack,
        Operation<Void> original,
        ItemStack sourceStack,
        int startIndex,
        int endIndex,
        boolean reverseDirection
    ) {
        original.call(slot, attemptedStack);
        int remainder = ContainerInsertHooks.remainderCountAfterEmptyPut(slot, attemptedStack);
        if (remainder > 0) {
            sourceStack.grow(remainder);
        }
    }

    @WrapOperation(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V",
            ordinal = 0
        )
    )
    private void stackupup$restoreRemainderAfterClickPut(
        Slot slot,
        ItemStack attemptedStack,
        Operation<Void> original,
        int slotId,
        int dragType,
        ClickType clickTypeIn,
        EntityPlayer player
    ) {
        original.call(slot, attemptedStack);
        int remainder = ContainerInsertHooks.remainderCountAfterEmptyPut(slot, attemptedStack);
        if (remainder <= 0) {
            return;
        }

        ItemStack cursorStack = player.inventory.getItemStack();
        if (cursorStack.isEmpty()) {
            ItemStack restored = attemptedStack.copy();
            restored.setCount(remainder);
            player.inventory.setItemStack(restored);
        } else {
            cursorStack.grow(remainder);
        }
    }
}
