package io.alexjoest.stackupup.mixin.late.cyclopscore;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code SimpleInventory implements INBTInventory} (which extends {@code IInventory}), so
 * {@code getInventoryStackLimit()} is an {@code IInventory} member: the reobfuscated release jar
 * declares {@code func_70297_j_()I}, the dev environment declares {@code getInventoryStackLimit()I}.
 * {@code remap = false} bypasses the refmap, hence both names are declared; omitting the SRG name
 * would leave the hook silently inert in production (the same failure that hid the RFTools scanner
 * truncation). {@code require = 0} keeps a version that inherits the getter from crashing the game.
 */
@Pseudo
@Mixin(targets = "org.cyclops.cyclopscore.inventory.SimpleInventory", remap = false)
abstract class SimpleInventoryMixin {

    @ModifyReturnValue(method = {"func_70297_j_()I", "getInventoryStackLimit()I"}, at = @At("RETURN"), require = 0)
    private int stackupup$expandDefaultInventoryLimit(int original) {
        if (StackLimitHooks.shouldBypassInventoryStackLimit(this)) {
            return original;
        }
        return StackLimitHooks.getInventoryCompatibilityStackSize(original);
    }
}
