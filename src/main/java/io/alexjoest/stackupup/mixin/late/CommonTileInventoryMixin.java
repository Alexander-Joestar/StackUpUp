package io.alexjoest.stackupup.mixin.late;

import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "mrriegel.limelib.tile.CommonTileInventory", remap = false)
abstract class CommonTileInventoryMixin {
    @ModifyConstant(method = "<init>(I)V", constant = @Constant(intValue = 64), require = 0)
    private static int stackupup$unlimitInventorySlots(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
