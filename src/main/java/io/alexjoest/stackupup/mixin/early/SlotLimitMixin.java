package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Slot.class)
public abstract class SlotLimitMixin {
    @ModifyReturnValue(
        method = "getItemStackLimit(Lnet/minecraft/item/ItemStack;)I",
        at = @At("RETURN")
    )
    private int stackupup$applyDynamicSlotLimit(int original, ItemStack stack) {
        int dynamic = StackLimitHooks.resolveDynamicSlotLimit(stack, original);
        // 动态上限不能超过库存的实际承受能力。
        // 若模组的 getInventoryStackLimit() 仍是 64（late mixin 未装载），
        // 则返还上限为 min(dynamic, 64)，不让 vanilla 投入超过库存容量的物品。
        // 这样也避免了 remainder 在"模组自行处理溢出"时造成重复。
        Slot self = (Slot) (Object) this;
        int invLimit = self.inventory.getInventoryStackLimit();
        if (invLimit > 0) {
            dynamic = Math.min(dynamic, invLimit);
        }
        return dynamic;
    }
}
