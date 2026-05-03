package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.alexjoest.stackupup.Constants;
import io.alexjoest.stackupup.ContainerInsertHooks;
import io.alexjoest.stackupup.RemainderGuard;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
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

    // ══════════════════ mergeItemStack ──────────────────────────────────

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

    // ══════════════════ slotClick ───────────────────────────────────────

    /**
     * ordinal 0 — QUICK_CRAFT 拖动过程中对逐个槽位的 putStack。
     * 余量暂存到 pendingDragRemainder，在拖动结束后的 setItemStack ordinal 0 中补回光标。
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 0)
    )
    private void stackupup$restoreRemainderAfterClickPut_0(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (clickTypeIn == ClickType.QUICK_CRAFT) {
            if (!RemainderGuard.enabled) { original.call(slot, attemptedStack); return; }
            int attemptedCount = attemptedStack.getCount();
            original.call(slot, attemptedStack);
            int remainder = ContainerInsertHooks.remainderAfterPut(slot, attemptedStack, attemptedCount);
            if (remainder > 0) { ContainerState.pendingDragRemainder.set(remainder); }
            return;
        }
        stackupup$restoreRemainderToCursor(slot, attemptedStack, original, player);
    }

    /**
     * ordinal 1 — PICKUP / 空槽写入 ("else" 分支的第一次 putStack)。
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 1)
    )
    private void stackupup$restoreRemainderAfterClickPut_1(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        stackupup$restoreRemainderToCursor(slot, attemptedStack, original, player);
    }

    /**
     * ordinal 2 — PICKUP / 空槽变空后 putStack(EMPTY)。安全忽略。
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 2)
    )
    private void stackupup$restoreRemainderAfterClickPut_2(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        original.call(slot, attemptedStack);
    }

    /**
     * ordinal 3 — PICKUP / decrStackSize 后 putStack(EMPTY)。安全忽略。
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 3)
    )
    private void stackupup$restoreRemainderAfterClickPut_3(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        original.call(slot, attemptedStack);
    }

    /**
     * ordinal 4 — PICKUP / 光标物品替换槽位物品 (SWAP 语义)。
     * 余量暂存到 pendingSwapRemainder，在随后的 setItemStack 中补回。
     */
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
        if (remainder > 0) { ContainerState.pendingSwapRemainder.set(remainder); }
    }

    /**
     * ordinal 5 — PICKUP / decrStackSize 清空后 putStack(EMPTY)。安全忽略。
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 5)
    )
    private void stackupup$restoreRemainderAfterClickPut_5(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        original.call(slot, attemptedStack);
    }

    /**
     * ordinal 6 — SWAP (hotbar) / 空槽后 putStack(EMPTY)。安全忽略。
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 6)
    )
    private void stackupup$restoreRemainderAfterClickPut_6(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        original.call(slot, attemptedStack);
    }

    /**
     * ordinal 7 — SWAP (hotbar) / splitStack 后写入。余量暂存，在 setItemStack/swap 中补回。
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 7)
    )
    private void stackupup$restoreRemainderAfterClickPut_7(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (!RemainderGuard.enabled) { original.call(slot, attemptedStack); return; }
        int attemptedCount = attemptedStack.getCount();
        original.call(slot, attemptedStack);
        int remainder = ContainerInsertHooks.remainderAfterPut(slot, attemptedStack, attemptedCount);
        if (remainder > 0) { ContainerState.pendingSwapRemainder.set(remainder); }
    }

    /**
     * ordinal 8 — SWAP (hotbar) / 完整物品写入。余量暂存。
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 8)
    )
    private void stackupup$restoreRemainderAfterClickPut_8(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (!RemainderGuard.enabled) { original.call(slot, attemptedStack); return; }
        int attemptedCount = attemptedStack.getCount();
        original.call(slot, attemptedStack);
        int remainder = ContainerInsertHooks.remainderAfterPut(slot, attemptedStack, attemptedCount);
        if (remainder > 0) { ContainerState.pendingSwapRemainder.set(remainder); }
    }

    /**
     * ordinal 9 — SWAP (hotbar, 有物品) / splitStack 后写入。余量暂存。
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 9)
    )
    private void stackupup$restoreRemainderAfterClickPut_9(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (!RemainderGuard.enabled) { original.call(slot, attemptedStack); return; }
        int attemptedCount = attemptedStack.getCount();
        original.call(slot, attemptedStack);
        int remainder = ContainerInsertHooks.remainderAfterPut(slot, attemptedStack, attemptedCount);
        if (remainder > 0) { ContainerState.pendingSwapRemainder.set(remainder); }
    }

    /**
     * ordinal 10 — SWAP (hotbar, 有物品) / 完整物品写入。余量暂存。
     */
    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;putStack(Lnet/minecraft/item/ItemStack;)V", ordinal = 10)
    )
    private void stackupup$restoreRemainderAfterClickPut_10(
            Slot slot, ItemStack attemptedStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (!RemainderGuard.enabled) { original.call(slot, attemptedStack); return; }
        int attemptedCount = attemptedStack.getCount();
        original.call(slot, attemptedStack);
        int remainder = ContainerInsertHooks.remainderAfterPut(slot, attemptedStack, attemptedCount);
        if (remainder > 0) { ContainerState.pendingSwapRemainder.set(remainder); }
    }

    // ══════════════════ slotClick shrink / grow ───────────────────────────

    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;shrink(I)V", ordinal = 0)
    )
    private void stackupup$delayCursorShrinkUntilSlotGrowth(
            ItemStack cursorStack, int quantity, Operation<Void> original
    ) {
        ContainerState.pendingMergeShrink.set(new StackUpUpMergeShrink(cursorStack, quantity, original));
    }

    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;grow(I)V", ordinal = 0)
    )
    private void stackupup$shrinkCursorByAcceptedSlotGrowth(
            ItemStack slotStack, int quantity, Operation<Void> original
    ) {
        StackUpUpMergeShrink pending = (StackUpUpMergeShrink) ContainerState.pendingMergeShrink.get();
        if (pending == null) { original.call(slotStack, quantity); return; }
        ContainerState.pendingMergeShrink.remove();
        int beforeCount = slotStack.getCount();
        original.call(slotStack, quantity);
        int accepted = Math.max(0, Math.min(pending.quantity, slotStack.getCount() - beforeCount));
        if (accepted > 0) { pending.originalShrink.call(pending.cursorStack, accepted); }
    }

    // ══════════════════ slotClick drop / setItemStack ────────────────────

    @WrapOperation(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;dropItem(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/entity/item/EntityItem;",
                    ordinal = 0
            )
    )
    private EntityItem stackupup$limitOutsideDropToDefaultSize(
            EntityPlayer droppingPlayer,
            ItemStack stack, boolean dropAround, Operation<EntityItem> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
        if (stack.getCount() > Constants.VANILLA_STACK_LIMIT) {
            ItemStack copy = stack.copy();
            stack.shrink(Constants.VANILLA_STACK_LIMIT);
            copy.setCount(Constants.VANILLA_STACK_LIMIT);
            EntityItem dropped = player.dropItem(copy, dropAround);
            player.inventory.setItemStack(stack);
            return dropped;
        }
        return original.call(stack, dropAround);
    }

    /**
     * ordinal 0 setItemStack — QUICK_CRAFT 拖动结束后的光标刷新。
     * 合并 pendingDragRemainder 到光标。
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
            ItemStack cursorStack, Operation<Void> original,
            int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player
    ) {
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
        original.call(cursorStack);
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

    // ══════════════════ 共享工具 ─────────────────────────────────────────

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
    private static void stackupup$clearPendingSlotClickState() {
        ContainerState.clear();
    }

    private static final class StackUpUpMergeShrink {
        private final ItemStack       cursorStack;
        private final int             quantity;
        private final Operation<Void> originalShrink;

        private StackUpUpMergeShrink(ItemStack cursorStack, int quantity, Operation<Void> originalShrink) {
            this.cursorStack    = cursorStack;
            this.quantity       = quantity;
            this.originalShrink = originalShrink;
        }
    }
}
