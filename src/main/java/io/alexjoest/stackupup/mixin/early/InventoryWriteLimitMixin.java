package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityLockableLoot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps native inventory getters unchanged while applying the item-aware rule at the loot-inventory write path.
 */
@Mixin(TileEntityLockableLoot.class)
public abstract class InventoryWriteLimitMixin {
    @WrapOperation(
        method = "setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/tileentity/TileEntityLockableLoot;getInventoryStackLimit()I"
        )
    )
    private int stackupup$resolveWriteLimit(
        TileEntityLockableLoot inventory,
        Operation<Integer> original,
        int slot,
        ItemStack stack
    ) {
        return StackLimitHooks.resolveInventoryWriteLimit(stack, inventory, original.call(inventory));
    }
}
