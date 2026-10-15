package io.alexjoest.stackupup.mixin.early;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackNbtMixin {
    @Shadow
    private int stackSize;

    @Inject(
            method = "<init>(Lnet/minecraft/nbt/NBTTagCompound;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/item/ItemStack;itemDamage:I",
                    shift = At.Shift.AFTER,
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void stackupup$readLargeCount(NBTTagCompound compound, CallbackInfo ci) {
        this.stackSize = compound.getInteger("Count");
    }

    @Inject(
            method = "writeToNBT(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
            at = @At("RETURN")
    )
    private void stackupup$writeLargeCount(NBTTagCompound nbt, CallbackInfoReturnable<NBTTagCompound> cir) {
        cir.getReturnValue().setInteger("Count", this.stackSize);
    }
}
