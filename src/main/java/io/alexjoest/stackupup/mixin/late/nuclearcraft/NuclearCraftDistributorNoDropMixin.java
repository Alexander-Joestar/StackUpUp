package io.alexjoest.stackupup.mixin.late.nuclearcraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "nc.multiblock.distributor.Distributor", remap = false)
abstract class NuclearCraftDistributorNoDropMixin {
    @Inject(method = "cullInventory()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void stackupup$noCullInventory(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "dropOverflow(Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void stackupup$noDropOverflow(CallbackInfo ci) {
        ci.cancel();
    }
}
