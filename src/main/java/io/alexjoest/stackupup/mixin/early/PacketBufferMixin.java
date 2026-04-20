package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.network.StackCountCodec;
import java.io.IOException;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PacketBuffer.class)
abstract class PacketBufferMixin {
    @Inject(
        method = "readItemStack()Lnet/minecraft/item/ItemStack;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void readLargeStacks(CallbackInfoReturnable<ItemStack> cir) throws IOException {
        PacketBuffer buffer = (PacketBuffer) (Object) this;
        int id = buffer.readShort();
        if (id < 0) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        int count = StackCountCodec.readCount(buffer);
        int damage = buffer.readShort();
        ItemStack stack = new ItemStack(Item.getItemById(id), count, damage);
        stack.getItem().readNBTShareTag(stack, buffer.readCompoundTag());
        cir.setReturnValue(stack);
    }

    @Inject(
        method = "writeItemStack(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/network/PacketBuffer;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void writeLargeStacks(ItemStack stack, CallbackInfoReturnable<PacketBuffer> cir) {
        PacketBuffer buffer = (PacketBuffer) (Object) this;
        if (stack.isEmpty()) {
            buffer.writeShort(-1);
            cir.setReturnValue(buffer);
            return;
        }

        buffer.writeShort(Item.getIdFromItem(stack.getItem()));
        StackCountCodec.writeCount(buffer, stack.getCount());
        buffer.writeShort(stack.getMetadata());

        NBTTagCompound tag = null;
        if (stack.getItem().isDamageable() || stack.getItem().getShareTag()) {
            tag = stack.getItem().getNBTShareTag(stack);
        }
        buffer.writeCompoundTag(tag);
        cir.setReturnValue(buffer);
    }
}
