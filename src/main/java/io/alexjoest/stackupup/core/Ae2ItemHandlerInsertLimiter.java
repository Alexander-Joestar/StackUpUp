package io.alexjoest.stackupup.core;

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
            return handler.insertItem(slot, stack, simulate);
        }

        int cap = insertionCap(handler, slot);
        if (cap <= 0) {
            return stack;
        }
        if (stack.getCount() <= cap) {
            return handler.insertItem(slot, stack, simulate);
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

            ItemStack remainder = handler.insertItem(slot, attempt, false);
            int remainderCount = stackCount(remainder);
            accepted += attemptCount - remainderCount;

            if (remainderCount > 0 || attemptCount <= remainderCount) {
                return remainderOf(stack, stack.getCount() - accepted);
            }
        }

        return ItemStack.EMPTY;
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
