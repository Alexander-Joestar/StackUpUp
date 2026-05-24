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
        "blusunrize.immersiveengineering.common.blocks.stone.TileEntityCokeOven",
        "blusunrize.immersiveengineering.common.blocks.stone.TileEntityBlastFurnace",
        "blusunrize.immersiveengineering.common.blocks.stone.TileEntityBlastFurnaceAdvanced",
        "blusunrize.immersiveengineering.common.blocks.stone.TileEntityAlloySmelter",
        "blusunrize.immersiveengineering.common.blocks.metal.TileEntityArcFurnace",
        "blusunrize.immersiveengineering.common.blocks.metal.TileEntitySqueezer",
        "blusunrize.immersiveengineering.common.blocks.metal.TileEntityFermenter",
        "blusunrize.immersiveengineering.common.blocks.metal.TileEntityMixer",
        "blusunrize.immersiveengineering.common.blocks.metal.TileEntityRefinery",
        "blusunrize.immersiveengineering.common.blocks.metal.TileEntityAssembler",
        "blusunrize.immersiveengineering.common.blocks.metal.TileEntityAutoWorkbench",
        "blusunrize.immersiveengineering.common.blocks.metal.TileEntityBottlingMachine",
        "blusunrize.immersiveengineering.common.blocks.metal.TileEntityBelljar",
        "blusunrize.immersiveengineering.common.blocks.metal.TileEntityToolbox",
        "blusunrize.immersiveengineering.common.blocks.wooden.TileEntityWoodenCrate",
        "blusunrize.immersiveengineering.common.blocks.wooden.TileEntityModWorkbench",
    },
    remap = false
)
abstract class IEMachineSlotLimitMixin {
    @Unique
    private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyReturnValue(method = "getSlotLimit", at = @At("RETURN"), require = 0)
    private int stackupup$expandSlotLimit(int original) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }
}
