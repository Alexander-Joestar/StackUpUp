package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SlotItemHandler.class, remap = false)
public abstract class SlotItemHandlerMixin {
    @Unique private static final int VANILLA_STACK_LIMIT = 64;

    @Shadow
    public abstract int getSlotStackLimit();

    /**
     * 仅当 item handler 的实际 slot limit 已经被扩展（gt 64）时才提升到全局兼容上限。
     * 若 handler 侧仍是 64，说明该 handler 未被 late mixin 扩展，向外虚报上限会导致
     * vanilla 过量投入 → handler 截断/丢弃 → remainder 与 handler 侧的溢出处理冲突。
     */
    @ModifyReturnValue(method = "getSlotStackLimit", at = @At("RETURN"))
    private int stackupup$replaceCompatibilityLimit(int original) {
        if (original == VANILLA_STACK_LIMIT) {
            return original; // handler 侧仍是 64，不予提升
        }
        return Math.max(original, StackLimitHooks.getCompatibilityStackSize());
    }

    @ModifyReturnValue(method = "getItemStackLimit", at = @At("RETURN"))
    private int stackupup$resolveItemAwareLimit(int original, ItemStack stack) {
        return StackLimitHooks.resolveItemHandlerSlotLimit(
            stack,
            original,
            this.getSlotStackLimit()
        );
    }
}
