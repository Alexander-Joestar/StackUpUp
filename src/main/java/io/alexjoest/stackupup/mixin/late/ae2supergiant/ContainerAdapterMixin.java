package io.alexjoest.stackupup.mixin.late.ae2supergiant;

import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Capacity mixin for the AE2-Supergiant {@code ContainerAdapter} {@code IInventory} bridge.
 *
 * <p>Source decision record
 * ({@code Applied-Energistics-2-Supergiant/src/main/java/ae2/api/inventories/ContainerAdapter.java}):
 * {@code getInventoryStackLimit()} starts from {@code int max = 64;} and folds every
 * {@code inventory.getSlotLimit(i)} into it with {@code Math.min}. The literal is the initial value
 * of a minimum, not the advertised limit, so replacing it turns "always at most 64" into "the
 * smallest per-slot limit, capped at the compatibility size". That is semantically safe: the getter
 * still advertises no more than the smallest limit a slot actually accepts, and the AE2S write path
 * ({@code inventory.setItemDirect} / {@code extractItem}) is untouched.</p>
 *
 * <p>Kept, not deleted. This file targets a genuinely different structural concern than the
 * transformer, though the literal is the same: {@code ae2.api.inventories.ContainerAdapter} is a
 * package-private class that {@code implements net.minecraft.inventory.IInventory} ({@code javap -p}
 * on {@code AppliedEnergistics2-Supergiant-1.0.15}), so the transformer <i>does</i> rewrite this
 * literal at class load and the two paths agree rather than fight. The reason this stays is that the
 * 64 here is <b>not</b> an advertised limit: it is the initial value of a {@code Math.min} fold. The
 * transformer would blindly turn it into the compatibility size, which is harmless because the fold
 * still takes the minimum; but that is an accident of this method's shape, not a guarantee. Keeping
 * the explicit constant hook puts the AE2S decision where a reader looks for it, with the
 * blacklist gate attached, and documents that the first fold value is allowed to exceed any single
 * slot's limit. Both paths resolve the same compatibility size, so the outcome is identical.
 *
 * <p><b>Method name:</b> {@code ContainerAdapter implements IInventory}, so the reobfuscated release
 * jar declares {@code getInventoryStackLimit()} as {@code func_70297_j_()I} and the MCP name exists
 * only in the dev environment. {@code remap = false} bypasses the refmap, so both names are declared;
 * with the MCP name alone this hook never matched in production. {@code require = 0} is intentional
 * here (unlike the RFTools scanner fix): this is the secondary path behind the class-load transformer,
 * and the literal it targets is an implementation detail of {@code int max = 64;}, so a fork that
 * folds the minimum differently should fall back to the transformer instead of aborting class load.</p>
 */
@Pseudo
@Mixin(targets = "ae2.api.inventories.ContainerAdapter", remap = false)
abstract class ContainerAdapterMixin {
    @ModifyConstant(
        method = {"func_70297_j_()I", "getInventoryStackLimit()I"},
        constant = @Constant(intValue = 64),
        require = 0
    )
    private int replaceCompatibilityLimit(int original) {
        if (StackLimitHooks.shouldBypassInventoryStackLimit(this)) {
            return original;
        }
        return StackLimitHooks.getInventoryCompatibilityStackSize(original);
    }
}
