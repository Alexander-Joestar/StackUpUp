package io.alexjoest.stackupup.mixin.late.farmersdelight;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
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
    @Unique
    private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyReturnValue(method = "getInventoryStackLimit()I", at = @At("RETURN"), require = 0)
    private static int stackupup$expandDefaultInventoryLimit(int original) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }
}
