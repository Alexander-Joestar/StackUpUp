package pl.asie.stackup.mixin.early

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import pl.asie.stackup.network.StackCountCodec
import java.io.IOException

@Mixin(PacketBuffer::class)
abstract class PacketBufferMixin {
    @Inject(method = ["readItemStack", "func_150791_c"], at = [At("HEAD")], cancellable = true)
    @Throws(IOException::class)
    private fun readLargeStacks(cir: CallbackInfoReturnable<ItemStack>) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        val buffer = this as PacketBuffer
        val id = buffer.readShort().toInt()
        if (id < 0) {
            cir.returnValue = ItemStack.EMPTY
            return
        }

        val count = StackCountCodec.readCount(buffer)
        val damage = buffer.readShort().toInt()
        val stack = ItemStack(Item.getItemById(id), count, damage)
        stack.item.readNBTShareTag(stack, buffer.readCompoundTag())
        cir.returnValue = stack
    }

    @Inject(method = ["writeItemStack", "func_150788_a"], at = [At("HEAD")], cancellable = true)
    private fun writeLargeStacks(stack: ItemStack, cir: CallbackInfoReturnable<PacketBuffer>) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        val buffer = this as PacketBuffer
        if (stack.isEmpty) {
            buffer.writeShort(-1)
            cir.returnValue = buffer
            return
        }

        buffer.writeShort(Item.getIdFromItem(stack.item))
        StackCountCodec.writeCount(buffer, stack.count)
        buffer.writeShort(stack.metadata)

        var tag: NBTTagCompound? = null
        if (stack.item.isDamageable || stack.item.getShareTag()) {
            tag = stack.item.getNBTShareTag(stack)
        }
        buffer.writeCompoundTag(tag)
        cir.returnValue = buffer
    }
}
