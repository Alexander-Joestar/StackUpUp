package io.alexjoest.stackupup.network

import io.netty.buffer.Unpooled
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StackCountCodecTest {
    @Test
    fun countsBetweenZeroAnd64_shouldKeepVanillaSingleByteEncoding() {
        for (count in listOf(0, 1, 64)) {
            val buffer = PacketBuffer(Unpooled.buffer())
            StackCountCodec.writeCount(buffer, count)

            assertEquals(1, buffer.readableBytes())
            assertEquals(count.toByte(), buffer.getByte(0))
            assertEquals(count, StackCountCodec.readCount(buffer))
            assertEquals(0, buffer.readableBytes())
        }
    }

    @Test
    fun otherIntegerCounts_shouldUseMagicAndIntEncoding() {
        for (count in listOf(65, 4096, -1, Int.MIN_VALUE, Int.MAX_VALUE)) {
            val buffer = PacketBuffer(Unpooled.buffer())
            StackCountCodec.writeCount(buffer, count)

            assertEquals(5, buffer.readableBytes())
            assertEquals((-42).toByte(), buffer.getByte(0))
            assertEquals(count, buffer.getInt(1))
            assertEquals(count, StackCountCodec.readCount(buffer))
            assertEquals(0, buffer.readableBytes())
        }
    }

    @Test
    fun itemStackPacketFields_shouldKeepVanillaOrderAroundExtendedCount() {
        val packet = PacketBuffer(Unpooled.buffer())
        val tag = NBTTagCompound().apply { setString("display", "demo") }
        packet.writeShort(0x1234)
        StackCountCodec.writeCount(packet, 65)
        packet.writeShort(0x2345)
        packet.writeCompoundTag(tag)

        assertEquals(0x1234, packet.readShort().toInt())
        assertEquals(65, StackCountCodec.readCount(packet))
        assertEquals(0x2345, packet.readShort().toInt())
        assertEquals("demo", requireNotNull(packet.readCompoundTag()).getString("display"))
        assertEquals(0, packet.readableBytes())
    }
}
