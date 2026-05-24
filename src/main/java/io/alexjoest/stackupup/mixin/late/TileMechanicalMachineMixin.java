package io.alexjoest.stackupup.mixin.late;

import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "org.cyclops.integrateddynamics.core.tileentity.TileMechanicalMachine", remap = false)
abstract class TileMechanicalMachineMixin {
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 64), require = 0)
    private static int stackupup$unlimitMachineSlots(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
