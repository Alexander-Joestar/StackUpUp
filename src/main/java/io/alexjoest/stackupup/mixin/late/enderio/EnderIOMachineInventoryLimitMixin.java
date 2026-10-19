package io.alexjoest.stackupup.mixin.late.enderio;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Capacity mixin for the legacy EnderIO machine base. **Not redundant** with
 * {@code InventoryStackLimitTransformer}, and this is the reason it must stay.
 *
 * <p>{@code javap -p -c EnderIO-CEu-5.5.1.jar} shows
 * {@code public abstract class ...AbstractInventoryMachineEntity extends
 * ...base.te.AbstractMachineEntity} with no {@code IInventory} in its own declaration, and the whole
 * ancestry ({@code AbstractMachineEntity} &rarr; {@code TileEntityEio} &rarr;
 * {@code com.enderio.core.common.TileEntityBase} &rarr; {@code TileEntity}) implements no such
 * interface either. The {@code IInventory} bridge is the separate inner class
 * {@code AbstractInventoryMachineEntity$InventoryWrapper}, whose {@code func_70297_j_()} simply
 * forwards to {@code this$0.getInventoryStackLimit()}. Because
 * {@code InventoryClassHierarchyRepository.isStackLimitTarget} matches only classes that really
 * implement {@code IInventory} / {@code IItemHandler} or extend {@code Slot}, it does <b>not</b> match
 * this class: the transformer never rewrites its {@code bipush 64} body. Deleting this mixin would
 * therefore drop every legacy EnderIO machine back to 64.
 *
 * <p>The class does declare a literal getter ({@code public int getInventoryStackLimit()} is
 * {@code bipush 64; ireturn}), which is why it looks like a transformer target; the missing
 * interface is what disqualifies it. Its {@code getInventoryStackLimit(int)} overload declares no
 * literal of its own (bytecode: {@code aload_0; invokevirtual getInventoryStackLimit:()I; ireturn}),
 * so this single hook also raises that overload by forwarding.
 *
 * <p>{@code crazypants.enderio.machines.machine.enchanter.TileEnchanter} was a second target until
 * its own {@code getInventoryStackLimit()} override was deleted here: that class <b>does</b>
 * {@code implement net.minecraft.inventory.ISidedInventory} (verified with {@code javap -p}), so its
 * getter is a genuine transformer target and the mixin entry only duplicated it.
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
