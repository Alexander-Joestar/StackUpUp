package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InventoryPlayer.class)
abstract class InventoryPlayerAddResourceMixin {
    @Redirect(
        method = "canMergeStacks(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;getInventoryStackLimit()I"
        )
    )
    private int stackupup$useMergeLimit(InventoryPlayer inventory, ItemStack existing, ItemStack incoming) {
        return StackLimitHooks.resolveInventoryClampLimit(incoming, inventory.getInventoryStackLimit());
    }

    @Redirect(
        method = "addResource(ILnet/minecraft/item/ItemStack;)I",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;getMaxStackSize()I"
            )
    )
    private int stackupup$usePickedItemLimit(ItemStack targetStack, int slot, ItemStack source) {
        return source.getMaxStackSize();
    }

    @Redirect(
            method = "addResource(ILnet/minecraft/item/ItemStack;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;getInventoryStackLimit()I"
            )
    )
    private int stackupup$usePickedStackLimit(InventoryPlayer inventory, int slot, ItemStack source) {
        return StackLimitHooks.resolveInventoryClampLimit(source, inventory.getInventoryStackLimit());
    }
}
