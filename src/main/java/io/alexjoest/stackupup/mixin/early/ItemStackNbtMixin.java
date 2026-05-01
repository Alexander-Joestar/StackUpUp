package io.alexjoest.stackupup.mixin.early;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
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

    @Shadow
    int itemDamage;

    @Shadow
    @Final
    private Item item;

    @Shadow
    @Nullable
    private NBTTagCompound stackTagCompound;

    @Shadow(remap = false)
    @Nullable
    private CapabilityDispatcher capabilities;

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

    @Inject(method = "writeToNBT", at = @At("HEAD"), cancellable = true)
    private void stackupup$writeLargeCount(NBTTagCompound nbt, CallbackInfoReturnable<NBTTagCompound> cir) {
        ResourceLocation location = Item.REGISTRY.getNameForObject(this.item);
        nbt.setString("id", location == null ? "minecraft:air" : location.toString());
        nbt.setInteger("Count", this.stackSize);
        nbt.setShort("Damage", (short) this.itemDamage);

        if (this.stackTagCompound != null) {
            nbt.setTag("tag", this.stackTagCompound);
        }

        if (this.capabilities != null) {
            NBTTagCompound caps = this.capabilities.serializeNBT();
            if (!caps.isEmpty()) {
                nbt.setTag("ForgeCaps", caps);
            }
        }

        cir.setReturnValue(nbt);
    }
}
