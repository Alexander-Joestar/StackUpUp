package pl.asie.stackup.network

import io.netty.buffer.Unpooled
import net.minecraft.network.PacketBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StackCountCodecTest {
    @Test
    fun `小于等于 64 的数量应保持单字节编码`() {
        val buffer = PacketBuffer(Unpooled.buffer())
        StackCountCodec.writeCount(buffer, 64)
        assertEquals(64, StackCountCodec.readCount(buffer))
    }

    @Test
    fun `大于 64 的数量应使用魔数加整型编码`() {
        val buffer = PacketBuffer(Unpooled.buffer())
        StackCountCodec.writeCount(buffer, 4096)
        assertEquals(4096, StackCountCodec.readCount(buffer))
    }
}
