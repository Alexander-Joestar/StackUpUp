package io.alexjoest.stackupup.mixin.late.rftools;

import net.minecraft.inventory.IInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Safety mixin for the RFTools storage scanner input slots.
 *
 * <p>{@code StorageScannerTileEntity#update} pushes the leftover of an injection back into its
 * input slots with a hardcoded limit:
 * {@code inventoryHelper.setInventorySlotContents(64, SLOT_IN, stack)} (and the same for
 * {@code SLOT_IN_AUTO}). The first parameter of
 * {@code InventoryHelper#setInventorySlotContents(int stackLimit, int index, ItemStack)} is a limit,
 * not an index: {@code McJtyLib} truncates {@code stack} to it in place
 * ({@code stack.setCount(stackLimit)}). That literal was harmless while the slot itself advertised
 * 64, but {@code mcjty.lib.container.DefaultSidedInventory} declares
 * {@code default int getInventoryStackLimit() { return 64; }} and
 * {@code InventoryStackLimitTransformer} now rewrites such interface default bodies, so the scanner's
 * input slots can legitimately hold more than 64. Without this mixin, a stack above 64 that the
 * scanner only partly consumes is truncated on the way back into the slot and the remainder is lost
 * (it is not returned to the caller either).</p>
 *
 * <p>The limit argument is therefore replaced with the receiver's own
 * {@code getInventoryStackLimit()}. No extra expansion switch is needed here: that getter already
 * resolves the expansion state and the capacity blacklist, so a disabled expansion or a blacklisted
 * receiver returns 64 and the original behavior is preserved exactly.</p>
 *
 * <p>{@code @ModifyArg} (Sponge Mixin) is used because MixinBooter 11.17 bundles a MixinExtras fork
 * without {@code com.llamalad7.mixinextras.injector.ModifyArg}; the Redirect and Overwrite injectors
 * are not acceptable substitutes here (repository rule: argument-level edit only, no control-flow
 * takeover). Both call sites share one injection point descriptor, so a single handler covers them.
 * The upstream RFTools class and both TianMi forks keep
 * the same package and the same two call sites, so one {@code targets} entry covers all three.</p>
 *
 * <p><b>Why two method names:</b> {@code remap = false} makes Mixin match the name that is really in
 * the class file, bypassing {@code mixins.StackUpUp.refmap.json}. {@code StorageScannerTileEntity}
 * implements {@code net.minecraft.util.ITickable}, so its {@code update()} override is a Minecraft
 * interface member and the reobfuscated release jar declares it under the SRG name
 * {@code func_73660_a()V} (production) while the dev environment, whose SRG&rarr;MCP deobfuscation
 * renames mod classes back, declares {@code update()V}. Declaring both names covers both
 * environments. {@code require = 1} is deliberate: the earlier shape ({@code method = "update"} with
 * {@code require = 0}) matched neither in production and skipped the injection in total silence,
 * which is exactly the field report — a stack above 64 left in the input slot was truncated back to
 * 64 on the next tick. "One of the two names must hit, or the mixin fails" is the only shape that
 * cannot regress back into a silent no-op. Mixin's callback-count check is aggregated over all
 * resolved target methods, so one matching name is enough.</p>
 */
@Pseudo
@Mixin(targets = "mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity", remap = false)
abstract class RftoolsStorageScannerLimitMixin {

    @ModifyArg(
        method = {"func_73660_a", "update"},
        at = @At(
            value = "INVOKE",
            target = "Lmcjty/lib/container/InventoryHelper;setInventorySlotContents(IILnet/minecraft/item/ItemStack;)V"
        ),
        index = 0,
        require = 1
    )
    private int stackupup$useReceiverInventoryLimit(int stackLimit) {
        return ((IInventory) (Object) this).getInventoryStackLimit();
    }
}
