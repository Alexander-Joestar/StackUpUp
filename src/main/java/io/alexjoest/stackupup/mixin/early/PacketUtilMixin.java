package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.network.StackCountCodec;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.PacketUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketUtil.class)
public abstract class PacketUtilMixin {
    @Inject(
        method = "writeItemStackFromClientToServer(Lnet/minecraft/network/PacketBuffer;Lnet/minecraft/item/ItemStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void writeLargeStacks(PacketBuffer buffer, ItemStack stack, CallbackInfo ci) {
        if (stack.isEmpty()) {
            buffer.writeShort(-1);
            ci.cancel();
            return;
        }

        buffer.writeShort(Item.getIdFromItem(stack.getItem()));
        StackCountCodec.writeCount(buffer, stack.getCount());
        buffer.writeShort(stack.getMetadata());

        NBTTagCompound tag = null;
        if (stack.getItem().isDamageable() || stack.getItem().getShareTag()) {
            tag = stack.getTagCompound();
        }
        buffer.writeCompoundTag(tag);
        ci.cancel();
    }
}
