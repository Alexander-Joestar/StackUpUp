package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.alexjoest.stackupup.ContainerState;
import io.alexjoest.stackupup.ContainerMergeShrink;
import io.alexjoest.stackupup.ContainerInsertHooks;
import io.alexjoest.stackupup.RemainderGuard;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Container.class)
public abstract class ContainerMixin {
    @Inject(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At("HEAD")
    )
    private void stackupup$clearPendingSlotClickStateBefore(
        int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        stackupup$clearPendingSlotClickState();

    }

    //

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

    @WrapOperation(
        method = "mergeItemStack(Lnet/minecraft/item/ItemStack;IIZ)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V"
        )
    )
    private void stackupup$restoreRemainderAfterMergePut(
        Slot slot, ItemStack attemptedStack, Operation<Void> original,
        ItemStack sourceStack, int startIndex, int endIndex, boolean reverseDirection
    ) {
        if (!RemainderGuard.enabled) { original.call(slot, attemptedStack); return; }
        int attemptedCount = attemptedStack.getCount();
        original.call(slot, attemptedStack);
        int remainder = ContainerInsertHooks.remainderAfterPut(slot, attemptedStack, attemptedCount);
        if (remainder > 0) { sourceStack.grow(remainder); }
    }

    // // slotClick

    @WrapOperation(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 0)
    )
    private void stackupup$restoreRemainderAfterClickPut_0(
        Slot slot, ItemStack attemptedStack, Operation<Void> original,
        int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (clickTypeIn == ClickType.QUICK_CRAFT) {
            original.call(slot, attemptedStack);
            return;
        }
        if (!RemainderGuard.enabled) { original.call(slot, attemptedStack); return; }
        int attemptedCount = attemptedStack.getCount();
        original.call(slot, attemptedStack);
        int remainder = ContainerInsertHooks.remainderAfterPut(slot, attemptedStack, attemptedCount);
        if (remainder <= 0) { return; }
        ItemStack cursorStack = player.inventory.getItemStack();
        if (cursorStack.isEmpty()) {
            ItemStack restored = attemptedStack.copy();
            restored.setCount(remainder);
            player.inventory.setItemStack(restored);
        } else {
            cursorStack.grow(remainder);
        }
    }

    @WrapOperation(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 1)
    )
    private void stackupup$restoreRemainderAfterClickPut_1(
        Slot slot, ItemStack attemptedStack, Operation<Void> original,
        int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (!RemainderGuard.enabled) { original.call(slot, attemptedStack); return; }
        int attemptedCount = attemptedStack.getCount();
        original.call(slot, attemptedStack);
        int remainder = ContainerInsertHooks.remainderAfterPut(slot, attemptedStack, attemptedCount);
        if (remainder <= 0) { return; }
        ItemStack cursorStack = player.inventory.getItemStack();
        if (cursorStack.isEmpty()) {
            ItemStack restored = attemptedStack.copy();
            restored.setCount(remainder);
            player.inventory.setItemStack(restored);
        } else {
            cursorStack.grow(remainder);
        }
    }

    @WrapOperation(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 4)
    )
    private void stackupup$restoreRemainderAfterClickPut_4(
        Slot slot, ItemStack attemptedStack, Operation<Void> original,
        int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (!RemainderGuard.enabled) { original.call(slot, attemptedStack); return; }
        int attemptedCount = attemptedStack.getCount();
        original.call(slot, attemptedStack);
        int remainder = ContainerInsertHooks.remainderAfterPut(slot, attemptedStack, attemptedCount);
        if (remainder > 0) { ContainerState.pendingMergeShrink.set(new ContainerMergeShrink(player.inventory.getItemStack(), remainder, original)); }
    }

    @WrapOperation(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 7)
    )
    private void stackupup$restoreRemainderAfterClickPut_7(
        Slot slot, ItemStack attemptedStack, Operation<Void> original,
        int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        original.call(slot, attemptedStack);
    }

    @WrapOperation(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 8)
    )
    private void stackupup$restoreRemainderAfterClickPut_8(
        Slot slot, ItemStack attemptedStack, Operation<Void> original,
        int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        original.call(slot, attemptedStack);
    }

    @WrapOperation(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 9)
    )
    private void stackupup$restoreRemainderAfterClickPut_9(
        Slot slot, ItemStack attemptedStack, Operation<Void> original,
        int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        original.call(slot, attemptedStack);
    }

    @WrapOperation(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 10)
    )
    private void stackupup$restoreRemainderAfterClickPut_10(
        Slot slot, ItemStack attemptedStack, Operation<Void> original,
        int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        original.call(slot, attemptedStack);
    }

    // // slotClick shrink / grow

    @WrapOperation(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;shrink(I)V", ordinal = 0)
    )
    private void stackupup$delayCursorShrinkUntilSlotGrowth(
        ItemStack cursorStack, int quantity, Operation<Void> original
    ) {
        ContainerState.pendingMergeShrink.set(new ContainerMergeShrink(cursorStack, quantity, original));
    }

    @WrapOperation(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;grow(I)V", ordinal = 0)
    )
    private void stackupup$shrinkCursorByAcceptedSlotGrowth(
        ItemStack slotStack, int quantity, Operation<Void> original
    ) {
        ContainerMergeShrink pending = ContainerState.pendingMergeShrink.get();
        if (pending == null) { original.call(slotStack, quantity); return; }
        ContainerState.pendingMergeShrink.remove();
        int beforeCount = slotStack.getCount();
        original.call(slotStack, quantity);
        int accepted = Math.max(0, Math.min(pending.quantity, slotStack.getCount() - beforeCount));
        if (accepted > 0) { pending.originalShrink.call(pending.cursorStack, accepted); }
    }

    // // slotClick setItemStack

    /**
     * ordinal 0 setItemStack // QUICK_CRAFT
     */
    @WrapOperation(
        method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;setItemStack(Lnet/minecraft/item/ItemStack;)V",
            ordinal = 0
        )
    )
    private void stackupup$applyDragRemainderToCursor(
        InventoryPlayer inventory, ItemStack cursorStack, Operation<Void> original,
        int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        original.call(inventory, cursorStack);
    }

    //

    @Unique
    private static void stackupup$clearPendingSlotClickState() {
        ContainerState.clear();
    }
}
