package io.alexjoest.stackupup.mixin.late;

import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "de.ellpeck.actuallyadditions.mod.tile.TileEntityInventoryBase", remap = false)
abstract class TileEntityInventoryBaseMixin {
    @ModifyConstant(method = "getMaxStackSize", constant = @Constant(intValue = 64), require = 0)
    private int replaceCompatibilityLimit(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
