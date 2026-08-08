package io.alexjoest.stackupup.core;

import io.alexjoest.stackupup.audit.ConservationAuditor;
import io.alexjoest.stackupup.audit.ConservationEvent;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.VanillaDoubleChestItemHandler;
import net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

public final class Ae2ItemHandlerInsertLimiter {
    private static final int VANILLA_STACK_LIMIT = 64;

    private Ae2ItemHandlerInsertLimiter() {
    }

    public static ItemStack insertCapped(IItemHandler handler, int slot, ItemStack stack, boolean simulate) {
        if (handler == null || stack == null || stack.isEmpty()) {
            return stack;
        }
        if (isTrusted(handler)) {
            return insertWithAudit(handler, slot, stack, simulate);
        }

        int cap = insertionCap(handler, slot);
        if (cap <= 0) {
            return stack;
        }
        if (stack.getCount() <= cap) {
            return insertWithAudit(handler, slot, stack, simulate);
        }
        if (simulate) {
            ItemStack attempt = stack.copy();
            attempt.setCount(cap);

            ItemStack remainder = handler.insertItem(slot, attempt, true);
            int accepted = cap - stackCount(remainder);
            return remainderOf(stack, stack.getCount() - accepted);
        }

        int accepted = 0;
        while (accepted < stack.getCount()) {
            int attemptCount = Math.min(cap, stack.getCount() - accepted);
            ItemStack attempt = stack.copy();
            attempt.setCount(attemptCount);

            ItemStack remainder = insertWithAudit(handler, slot, attempt, false);
            int remainderCount = stackCount(remainder);
            accepted += attemptCount - remainderCount;

            if (remainderCount > 0 || attemptCount <= remainderCount) {
                return remainderOf(stack, stack.getCount() - accepted);
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * 真实插入并做守恒审计。simulate=true 或审计未开启时直通原调用，不做任何额外读取；
     * 开启时在插入前后各读取一次槽内数量，按守恒公式判定并记录。
     */
    private static ItemStack insertWithAudit(IItemHandler handler, int slot, ItemStack stack, boolean simulate) {
        if (simulate || !ConservationAuditor.enabled()) {
            return handler.insertItem(slot, stack, simulate);
        }
        int before = stackCount(handler.getStackInSlot(slot));
        ItemStack remainder = handler.insertItem(slot, stack, false);
        int after = stackCount(handler.getStackInSlot(slot));
        ConservationAuditor.audit(new ConservationEvent(
            "Ae2ItemHandlerInsertLimiter#insertCapped",
            handler.getClass().getName(),
            slot,
            false,
            stackCount(stack),
            before,
            after,
            stackCount(remainder)
        ));
        return remainder;
    }

    private static int insertionCap(IItemHandler handler, int slot) {
        return Math.min(VANILLA_STACK_LIMIT, handler.getSlotLimit(slot));
    }

    private static int stackCount(ItemStack stack) {
        return stack == null || stack.isEmpty() ? 0 : stack.getCount();
    }

    private static ItemStack remainderOf(ItemStack source, int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = source.copy();
        remainder.setCount(Math.min(source.getCount(), count));
        return remainder;
    }

    private static boolean isTrusted(IItemHandler handler) {
        // 保守白名单：只直通 Forge fixed compat 已覆盖且不会委托任意第三方 handler 的基础实现。
        return handler instanceof ItemStackHandler
            || handler instanceof VanillaDoubleChestItemHandler
            || handler instanceof EntityEquipmentInvWrapper
            || handler instanceof EmptyHandler
            || handler instanceof InvWrapper
            || handler instanceof SidedInvWrapper;
    }
}
