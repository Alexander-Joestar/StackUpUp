package io.alexjoest.stackupup.mixin.late;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandlerPortable;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = ItemGridHandlerPortable.class, remap = false)
public abstract class ItemGridHandlerPortableMixin {
    @WrapOperation(
        method = "onExtract(Lnet/minecraft/entity/player/EntityPlayerMP;Ljava/util/UUID;II)V",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(JJ)J"),
        require = 0
    )
    private long stackupup$expandDefaultExtractLimit(
        long size,
        long maxItemSize,
        Operation<Long> original
    ) {
        return StackLimitHooks.expandDefaultExtractLimit(size, maxItemSize);
    }
}
