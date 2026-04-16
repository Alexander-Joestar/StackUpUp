package pl.asie.stackup.mixin.early

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer
import net.minecraftforge.common.util.PacketUtil
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import pl.asie.stackup.network.StackCountCodec

@Mixin(PacketUtil::class)
abstract class PacketUtilMixin {
    companion object {
        @Inject(method = ["writeItemStackFromClientToServer"], at = [At("HEAD")], cancellable = true)
        @JvmStatic
        private fun writeLargeStacks(buffer: PacketBuffer, stack: ItemStack, ci: CallbackInfo) {
            if (stack.isEmpty) {
                buffer.writeShort(-1)
                ci.cancel()
                return
            }

            buffer.writeShort(Item.getIdFromItem(stack.item))
            StackCountCodec.writeCount(buffer, stack.count)
            buffer.writeShort(stack.metadata)

            var tag: NBTTagCompound? = null
            if (stack.item.isDamageable || stack.item.getShareTag()) {
                tag = stack.tagCompound
            }
            buffer.writeCompoundTag(tag)
            ci.cancel()
        }
    }
}
