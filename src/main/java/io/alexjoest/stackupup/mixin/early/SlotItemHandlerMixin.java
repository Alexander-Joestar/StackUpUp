package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * SlotItemHandler 只保留自洽的 simulate 基广告（T3 收敛）。
 *
 * <p>按 T2a 登记表（docs/agent/t2a-容量站点登记表.md §2.2）逐方法判定：
 *
 * <ul>
 *   <li>getSlotStackLimit()I：转发。广告转发自 itemHandler.getSlotLimit(index)
 *       （SlotItemHandler.java:107-110），handler 侧才是真实容量来源；T3 已移除其独立动态上限注入
 *       （原 Math.max(original, compat) 会把槽位广告抬到 handler 真实上限之上），槽位上限自然跟随
 *       handler 侧真实来源（如 ItemStackHandler 被 ForgeItemHandlerLimitMixin 提升）。</li>
 *   <li>getItemStackLimit(ItemStack)I：自洽。广告值由 handler 侧 insertItem(..., true) simulate 求得
 *       （SlotItemHandler.java:118-134），与写入面同源，再由 resolveItemHandlerSlotLimit 按
 *       min(slotLimit, itemLimit) 收敛，保留。</li>
 * </ul>
 */
@Mixin(value = SlotItemHandler.class, remap = false)
public abstract class SlotItemHandlerMixin {
    @Shadow
    public abstract int getSlotStackLimit();

    @ModifyReturnValue(method = "getItemStackLimit", at = @At("RETURN"))
    private int stackupup$resolveItemAwareLimit(int original, ItemStack stack) {
        return StackLimitHooks.resolveItemHandlerSlotLimit(
            stack,
            original,
            this.getSlotStackLimit()
        );
    }
}
