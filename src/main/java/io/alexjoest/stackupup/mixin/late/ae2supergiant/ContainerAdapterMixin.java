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
 * <p>Kept, not deleted. The transformer ({@code InventoryStackLimitTransformer}) already rewrites the
 * same literal at class load, so removing this mixin would leave AE2S container capacity defined
 * solely by that class-load rewrite while this file still looks like it owns the getter. Keeping the
 * explicit constant hook states the AE2S decision where a reader looks for it, and both paths
 * resolve the same compatibility size, so they agree rather than fight.</p>
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
