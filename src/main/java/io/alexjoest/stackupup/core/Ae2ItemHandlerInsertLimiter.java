package io.alexjoest.stackupup.core;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.VanillaDoubleChestItemHandler;
import net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper;
import net.minecraftforge.items.wrapper.EmptyHandler;

/**
 * 投喂限幅工具：只在测试与探针中直接使用。
 *
 * 方案 A 之后，AE2 热路径已改为 mixin 原样透传（见 AppEngAdaptorItemHandlerMixin），本类不再处于热路径上，
 * 保留为纯工具类供 WrapperCapacityDiagnosticTest / Ae2ItemHandlerInsertLimiterTest 等行为护栏使用；
 * 原 ConservationAuditor 审计分支已随审计器移除，本类不再产生任何守恒事件。
 */
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
        // 保守白名单（T10 重排，证据见 docs/agent/compatibility-decision-record.md §3.6）：
        // 只直通写入链全部由项目源码闭合的实现：
        // - ItemStackHandler / EntityEquipmentInvWrapper：insertItem 自行计算 limit 并返回 remainder（自洽）；
        // - VanillaDoubleChestItemHandler：delegate 固定为原版箱体 TileEntityChest
        //   （TileEntityChest#getSingleChestHandler → TileEntityLockable#createUnSidedHandler 的 InvWrapper(chest)），
        //   写入面经 TileEntityLockableLoot#setInventorySlotContents 按同一 getInventoryStackLimit 夹取闭合；
        // - EmptyHandler：零容量拒绝目标，无写入路径，原样返回输入。
        // 转发 wrapper（InvWrapper/SidedInvWrapper）的 delegate 是任意 IInventory/ISidedInventory，
        // 第三方写入面无源码不可判定，不再直通；走 min(64, getSlotLimit) 限流分片。
        // 本类已不在热路径上（AE2 投喂改为 mixin 透传），白名单仅服务测试护栏，不再承担运行期审计。
        return handler instanceof ItemStackHandler
            || handler instanceof VanillaDoubleChestItemHandler
            || handler instanceof EntityEquipmentInvWrapper
            || handler instanceof EmptyHandler;
    }
}
