package io.alexjoest.stackupup.mixin.late;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets = {
        "crazypants.enderio.machines.machine.soul.TileSoulBinder",
        "crazypants.enderio.machines.machine.slicensplice.TileSliceAndSplice",
        "crazypants.enderio.machines.machine.farm.TileFarmStation"
    },
    remap = false
)
abstract class EnderIOSlottedInventoryLimitMixin {
    @Unique
    private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyReturnValue(method = "getInventoryStackLimit(I)I", at = @At("RETURN"), require = 0)
    private int stackupup$expandDefaultSlottedLimit(int original, int slot) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }
}
