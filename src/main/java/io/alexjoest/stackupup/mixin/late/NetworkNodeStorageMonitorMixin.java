package io.alexjoest.stackupup.mixin.late;

import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeStorageMonitor", remap = false)
abstract class NetworkNodeStorageMonitorMixin {
    @ModifyConstant(method = "extract", constant = @Constant(intValue = 64), require = 0)
    private int stackupupReplaceExtractLimit(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
