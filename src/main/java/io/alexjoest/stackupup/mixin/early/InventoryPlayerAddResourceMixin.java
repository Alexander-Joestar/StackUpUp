package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InventoryPlayer.class)
abstract class InventoryPlayerAddResourceMixin {
    @ModifyExpressionValue(
        method = "canMergeStacks(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;getInventoryStackLimit()I"
        )
    )
    private int stackupup$useMergeLimit(int original, ItemStack existing, ItemStack incoming) {
        // 非静态 handler 的 this 是真实 InventoryPlayer receiver，用于 owner 黑名单归属判断。
        return StackLimitHooks.resolveInventoryClampLimit(incoming, (InventoryPlayer) (Object) this, original);
    }

    @ModifyExpressionValue(
        method = "addResource(ILnet/minecraft/item/ItemStack;)I",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;getMaxStackSize()I"
            )
    )
    private static int stackupup$usePickedItemLimit(int original, int slot, ItemStack source) {
        return source.getMaxStackSize();
    }

    @ModifyExpressionValue(
            method = "addResource(ILnet/minecraft/item/ItemStack;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;getInventoryStackLimit()I"
            )
    )
    private int stackupup$usePickedStackLimit(int original, int slot, ItemStack source) {
        return StackLimitHooks.resolveInventoryClampLimit(source, (InventoryPlayer) (Object) this, original);
    }
}
