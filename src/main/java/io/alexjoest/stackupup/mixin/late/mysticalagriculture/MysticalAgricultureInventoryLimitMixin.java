package io.alexjoest.stackupup.mixin.late.mysticalagriculture;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Capacity mixin for the Mystical Agriculture essence reprocessor base only.
 *
 * <p>{@code TileEssenceReprocessor} is {@code public abstract class} and its inventory stack limit
 * getter is a literal {@code return 64;} (bytecode: {@code bipush 64; ireturn}). None of the six
 * concrete machine subclasses declares the getter, so every one of them inherits that literal.
 * {@code InventoryStackLimitTransformer} now rewrites abstract classes too, so both paths target the
 * same bytecode and this mixin degrades to a passthrough ({@code original} is already the expanded
 * limit when it runs); it stays as a defensive second path to those machines' advertised capacity.
 * The write side needs no hook: the same getter is what
 * {@code setInventorySlotContents} clamps against.</p>
 *
 * <p>{@code javap} on the mod jar shows the getter under {@code func_70297_j_}, so that SRG name is
 * what has to match at runtime; the deobfuscated {@code getInventoryStackLimit} is registered
 * alongside it for targets that are presented under MCP names, as the other optional capacity
 * targets in this repository already do. {@code remap = false} means neither name is rewritten for
 * us, and {@code require = 0} keeps the one that is absent from logging a failure.</p>
 */
@Pseudo
@Mixin(targets = "com.blakebr0.mysticalagriculture.tileentity.reprocessor.TileEssenceReprocessor", remap = false)
abstract class MysticalAgricultureInventoryLimitMixin {

    @ModifyReturnValue(method = {"func_70297_j_()I", "getInventoryStackLimit()I"}, at = @At("RETURN"), require = 0)
    private int stackupup$expandReprocessorInventoryLimit(int original) {
        if (StackLimitHooks.shouldBypassInventoryStackLimit(this)) {
            return original;
        }
        return StackLimitHooks.getInventoryCompatibilityStackSize(original);
    }
}
