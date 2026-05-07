package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.alexjoest.stackupup.ContainerMergeShrink;
import io.alexjoest.stackupup.ContainerState;
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
        ContainerState.isDropOperation.set(slotId == -999);
    }

    // ?????????????????? mergeItemStack ??????????????????????????????????

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

    // ?????????????????? slotClick ???????????????????????????????????????

    @Unique
    private static boolean stackupup$shouldBypassRemainder() {
        return ContainerState.isDropOperation.get();
    }

    /**
     * ordinal 0 ? QUICK_CRAFT ??????????? putStack?
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 0)
    )
    private void stackupup$restoreRemainderAfterClickPut_0(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slot, attemptedStack); return; }
        if (clickTypeIn == ClickType.QUICK_CRAFT) {
            stackupup$storeRemainder(slot, attemptedStack, original, ContainerState.pendingDragRemainder);
            return;
        }
        stackupup$restoreRemainderToCursor(slot, attemptedStack, original, player);
    }

    /**
     * ordinal 1 ? PICKUP / ?????
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 1)
    )
    private void stackupup$restoreRemainderAfterClickPut_1(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slot, attemptedStack); return; }
        stackupup$restoreRemainderToCursor(slot, attemptedStack, original, player);
    }

    /**
     * ordinal 2 ? PICKUP / ????? putStack(EMPTY)??????
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 2)
    )
    private void stackupup$restoreRemainderAfterClickPut_2(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slot, attemptedStack); return; }
        original.call(slot, attemptedStack);
    }

    /**
     * ordinal 3 ? PICKUP / decrStackSize ? putStack(EMPTY)??????
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 3)
    )
    private void stackupup$restoreRemainderAfterClickPut_3(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slot, attemptedStack); return; }
        original.call(slot, attemptedStack);
    }

    /**
     * ordinal 4 ? PICKUP / ?????????? (SWAP)?
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 4)
    )
    private void stackupup$restoreRemainderAfterClickPut_4(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slot, attemptedStack); return; }
        stackupup$storeRemainder(slot, attemptedStack, original, ContainerState.pendingSwapRemainder);
    }

    /**
     * ordinal 5 ? PICKUP / decrStackSize ??? putStack(EMPTY)??????
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 5)
    )
    private void stackupup$restoreRemainderAfterClickPut_5(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slot, attemptedStack); return; }
        original.call(slot, attemptedStack);
    }

    /**
     * ordinal 6 ? SWAP (hotbar) / ??? putStack(EMPTY)??????
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 6)
    )
    private void stackupup$restoreRemainderAfterClickPut_6(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slot, attemptedStack); return; }
        original.call(slot, attemptedStack);
    }

    /**
     * ordinal 7 ? SWAP (hotbar) / splitStack ????
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 7)
    )
    private void stackupup$restoreRemainderAfterClickPut_7(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slot, attemptedStack); return; }
        stackupup$storeRemainder(slot, attemptedStack, original, ContainerState.pendingSwapRemainder);
    }

    /**
     * ordinal 8 ? SWAP (hotbar) / ???????
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 8)
    )
    private void stackupup$restoreRemainderAfterClickPut_8(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slot, attemptedStack); return; }
        stackupup$storeRemainder(slot, attemptedStack, original, ContainerState.pendingSwapRemainder);
    }

    /**
     * ordinal 9 ? SWAP (hotbar, ???) / splitStack ????
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 9)
    )
    private void stackupup$restoreRemainderAfterClickPut_9(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slot, attemptedStack); return; }
        stackupup$storeRemainder(slot, attemptedStack, original, ContainerState.pendingSwapRemainder);
    }

    /**
     * ordinal 10 ? SWAP (hotbar, ???) / ???????
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 10)
    )
    private void stackupup$restoreRemainderAfterClickPut_10(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slot, attemptedStack); return; }
        stackupup$storeRemainder(slot, attemptedStack, original, ContainerState.pendingSwapRemainder);
    }

    // ?????????????????? slotClick shrink / grow ???????????????????????????

    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;shrink(I)V", ordinal = 0)
    )
    private void stackupup$delayCursorShrinkUntilSlotGrowth(
            ItemStack cursorStack, int quantity, Operation<Void> original
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(cursorStack, quantity); return; }
        ContainerState.pendingMergeShrink.set(new ContainerMergeShrink(cursorStack, quantity, original));
    }

    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;grow(I)V", ordinal = 0)
    )
    private void stackupup$shrinkCursorByAcceptedSlotGrowth(
            ItemStack slotStack, int quantity, Operation<Void> original
    ) {
        if (stackupup$shouldBypassRemainder()) { original.call(slotStack, quantity); return; }
        ContainerMergeShrink pending = ContainerState.pendingMergeShrink.get();
        if (pending == null) { original.call(slotStack, quantity); return; }
        ContainerState.pendingMergeShrink.remove();
        int beforeCount = slotStack.getCount();
        original.call(slotStack, quantity);
        int accepted = Math.max(0, Math.min(pending.quantity, slotStack.getCount() - beforeCount));
        if (accepted > 0) { pending.originalShrink.call(pending.cursorStack, accepted); }
    }

    // ?????????????????? slotClick setItemStack ????????????????????????????

    /**
     * ordinal 0 setItemStack ? QUICK_CRAFT ???????????
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
        if (stackupup$shouldBypassRemainder()) { original.call(inventory, cursorStack); return; }
        Integer pendingDrag = ContainerState.pendingDragRemainder.get();
        if (pendingDrag != null) {
            ContainerState.pendingDragRemainder.remove();
            if (pendingDrag > 0 && !cursorStack.isEmpty()) { cursorStack.grow(pendingDrag); }
        }
        Integer pendingSwap = ContainerState.pendingSwapRemainder.get();
        if (pendingSwap != null) {
            ContainerState.pendingSwapRemainder.remove();
            if (pendingSwap > 0 && !cursorStack.isEmpty()) { cursorStack.grow(pendingSwap); }
        }
        original.call(inventory, cursorStack);
    }

    @Inject(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At("RETURN")
    )
    private void stackupup$clearPendingSlotClickStateAfter(
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        stackupup$clearPendingSlotClickState();
    }

    // ?????????????????? ???? ?????????????????????????????????????????

    @Unique
    private void stackupup$restoreRemainderToCursor(
            Slot slot, ItemStack attemptedStack, Operation<Void> original, EntityPlayer player
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

    @Unique
    private void stackupup$storeRemainder(
            Slot slot, ItemStack attemptedStack, Operation<Void> original, ThreadLocal<Integer> pendingRemainder
    ) {
        if (!RemainderGuard.enabled) { original.call(slot, attemptedStack); return; }
        int attemptedCount = attemptedStack.getCount();
        original.call(slot, attemptedStack);
        int remainder = ContainerInsertHooks.remainderAfterPut(slot, attemptedStack, attemptedCount);
        if (remainder > 0) { pendingRemainder.set(remainder); }
    }

    @Unique
    private static void stackupup$clearPendingSlotClickState() {
        ContainerState.clear();
    }
}
