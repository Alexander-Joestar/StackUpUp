package io.alexjoest.stackupup.mixin.late.enderio;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Capacity mixin for the legacy EnderIO machine base only.
 *
 * <p>{@code AbstractInventoryMachineEntity} is {@code public abstract class} and returns a literal
 * 64 from {@code getInventoryStackLimit()}. {@code InventoryStackLimitTransformer} now rewrites
 * abstract classes as well, so both paths target the same bytecode and this mixin degrades to a
 * passthrough ({@code original} is already the expanded limit when it runs). It is kept as a
 * defensive second path for the concrete machines that inherit that getter.</p>
 *
 * <p>{@code crazypants.enderio.machines.machine.enchanter.TileEnchanter} was a second target until
 * its own {@code getInventoryStackLimit()} override (a literal {@code return 64;} on a concrete
 * class) was deleted here: the structural transformer already rewrites that bytecode at class load,
 * so the mixin entry only duplicated it.</p>
 */
@Pseudo
@Mixin(
        targets = {"crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity"},
        remap = false
)
abstract class EnderIOMachineInventoryLimitMixin {

    @ModifyReturnValue(method = "getInventoryStackLimit()I", at = @At("RETURN"), require = 0)
    private int stackupup$expandDefaultMachineLimit(int original) {
        if (StackLimitHooks.shouldBypassInventoryStackLimit(this)) {
            return original;
        }
        return StackLimitHooks.getInventoryCompatibilityStackSize(original);
    }
}
