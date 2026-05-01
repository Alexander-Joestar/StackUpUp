package io.alexjoest.stackupup.network

import io.alexjoest.stackupup.Constants
import net.minecraft.network.PacketBuffer

object StackCountCodec {
    // 数量小于等于 64 时保持原版单字节编码，避免无意义放大网络包。
    @JvmStatic
    fun writeCount(buffer: PacketBuffer, count: Int) {
        if (count in 0..64) {
            buffer.writeByte(count)
        } else {
            buffer.writeByte(Constants.COUNT_MAGIC)
            buffer.writeInt(count)
        }
    }

    // 读取时先看首字节是否为魔数，命中后再补读完整整型数量。
    @JvmStatic
    fun readCount(buffer: PacketBuffer): Int {
        val marker = buffer.readByte().toInt()
        return if (marker == Constants.COUNT_MAGIC) buffer.readInt() else marker
    }
}
