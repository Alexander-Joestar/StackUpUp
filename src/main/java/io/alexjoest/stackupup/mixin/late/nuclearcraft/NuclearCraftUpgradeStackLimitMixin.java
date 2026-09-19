package io.alexjoest.stackupup.mixin.late.nuclearcraft;

import io.alexjoest.stackupup.compat.nuclearcraft.NuclearCraftCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "nc.config.NCConfig", remap = false)
abstract class NuclearCraftUpgradeStackLimitMixin {
    @Shadow(remap = false)
    private static int[] upgrade_stack_sizes;

    @Inject(method = "syncConfig(Z)V", at = @At("RETURN"), require = 0)
    private static void stackupup$applyUpgradeStackLimits(boolean loadFromFile, CallbackInfo ci) {
        NuclearCraftCompat.rememberUpgradeStackSizes(upgrade_stack_sizes);
    }
}
