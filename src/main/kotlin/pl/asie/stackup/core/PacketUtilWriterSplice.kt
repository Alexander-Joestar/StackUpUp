package pl.asie.stackup.core

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer
import pl.asie.stackup.Constants

object PacketUtilWriterSplice {
    @JvmStatic
    fun writeItemStackFromClientToServer(buffer: PacketBuffer, stack: ItemStack) {
        if (stack.isEmpty) {
            buffer.writeShort(-1)
        } else {
            buffer.writeShort(Item.getIdFromItem(stack.item))
            if (stack.count in 0..64) {
                buffer.writeByte(stack.count)
            } else {
                buffer.writeByte(Constants.COUNT_MAGIC)
                buffer.writeInt(stack.count)
            }
            buffer.writeShort(stack.metadata)
            var tag: NBTTagCompound? = null

            if (stack.item.isDamageable || stack.item.getShareTag()) {
                tag = stack.tagCompound
            }

            buffer.writeCompoundTag(tag)
        }
    }
}
