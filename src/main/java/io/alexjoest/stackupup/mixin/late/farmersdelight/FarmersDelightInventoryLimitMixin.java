package io.alexjoest.stackupup.mixin.late.farmersdelight;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
        targets = {
                "com.wdcftgg.farmersdelightlegacy.common.tile.TileEntityBasket",
                "com.wdcftgg.farmersdelightlegacy.common.tile.TileEntityCabinet",
                "com.wdcftgg.farmersdelightlegacy.common.tile.TileEntityCookingPot",
                "com.wdcftgg.farmersdelightlegacy.common.tile.TileEntitySkillet"
        },
        remap = false
)
abstract class FarmersDelightInventoryLimitMixin {

    @ModifyReturnValue(method = {"getInventoryStackLimit()I", "func_70297_j_()I"}, at = @At("RETURN"), require = 0)
    private int stackupup$expandDefaultInventoryLimit(int original) {
        if (StackLimitHooks.shouldBypassInventoryStackLimit(this)) {
            return original;
        }
        return StackLimitHooks.getInventoryCompatibilityStackSize(original);
    }
}
