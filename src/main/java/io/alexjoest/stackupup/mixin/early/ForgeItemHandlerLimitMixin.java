package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Forge 自洽 handler 的 getSlotLimit 提升（T3 收敛后只保留两个自洽目标）。
 *
 * <p>按 T2a 登记表（docs/agent/t2a-容量站点登记表.md §2.3）与兼容决策记录 §3.5 的逐目标判定：
 *
 * <ul>
 *   <li>ItemStackHandler：自洽。insertItem 写入前重读 getStackLimit = min(getSlotLimit, maxStackSize)
 *       （ItemStackHandler.java:88、:162-165），达上限写入 limit 并返回 remainder（:107-116）。</li>
 *   <li>EntityEquipmentInvWrapper：自洽（P0 事实 a）。insertItem 内 limit = getStackLimit（:95），
 *       simulate=false 时经 setItemStackToSlot / grow 落库（:108-120），超量返回 remainder（:122）；
 *       vanilla 实体 setter（EntityLiving.java:1012-1022、EntityPlayer.java:2432-2449、
 *       EntityArmorStand.java:155-167）为无截断的列表直写，真实容量由 wrapper 的上限计算闭合。</li>
 *   <li>InvWrapper / SidedInvWrapper / CombinedInvWrapper / RangedWrapper：转发（查询与写入委托底层
 *       IInventory / 子 handler），不是独立容量来源，T3 已从本 mixin 移除；其 getSlotLimit 自然转发
 *       底层真实来源，由底层站点（vanilla 库存、ItemStackHandler）接管。</li>
 * </ul>
 *
 * <p>getSlotLimit 的取值语义来自源码：ItemStackHandler.java:157-160 恒返回 64；
 * EntityEquipmentInvWrapper.java:162-166 装甲槽返回 1、其余返回 64。这里只把原版默认 64 提升到全局
 * 兼容上限；装甲槽 1 与任何自定义值一律保持原值。该值检查是目标类的取值语义，不是「该目标是否可 patch」
 * 的准入判据——目标集合由登记表三分类固定。
 */
@Mixin(
    value = {
        ItemStackHandler.class,
        EntityEquipmentInvWrapper.class
    },
    remap = false
)
public abstract class ForgeItemHandlerLimitMixin {
    @Unique private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyReturnValue(method = "getSlotLimit", at = @At("RETURN"))
    private int stackupup$replaceCompatibilityLimit(int original, int slot) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }
}
