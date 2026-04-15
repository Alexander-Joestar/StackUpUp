package pl.asie.stackup.core

import io.netty.buffer.ByteBuf
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer
import pl.asie.stackup.Constants
import java.io.IOException

class PacketBufferWriterSplice(wrapped: ByteBuf) : PacketBuffer(wrapped) {
    @Throws(IOException::class)
    override fun readItemStack(): ItemStack {
        val id = this.readShort().toInt()
        return if (id < 0) {
            ItemStack.EMPTY
        } else {
            var count = this.readByte().toInt()
            if (count == Constants.COUNT_MAGIC) {
                count = this.readInt()
            }
            val damage = this.readShort().toInt()
            val itemstack = ItemStack(Item.getItemById(id), count, damage)
            itemstack.item.readNBTShareTag(itemstack, this.readCompoundTag())
            itemstack
        }
    }

    override fun writeItemStack(stack: ItemStack): PacketBuffer {
        if (stack.isEmpty) {
            this.writeShort(-1)
        } else {
            this.writeShort(Item.getIdFromItem(stack.item))
            if (stack.count in 0..64) {
                this.writeByte(stack.count)
            } else {
                this.writeByte(Constants.COUNT_MAGIC)
                this.writeInt(stack.count)
            }
            this.writeShort(stack.metadata)
            var tag: NBTTagCompound? = null

            if (stack.item.isDamageable || stack.item.getShareTag()) {
                tag = stack.item.getNBTShareTag(stack)
            }

            this.writeCompoundTag(tag)
        }

        return this
    }
}
