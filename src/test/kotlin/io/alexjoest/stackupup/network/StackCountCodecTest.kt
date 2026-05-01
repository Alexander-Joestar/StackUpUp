package io.alexjoest.stackupup.network

import io.netty.buffer.Unpooled
import net.minecraft.network.PacketBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StackCountCodecTest {
    @Test
    fun `countLeq64_shouldUseSingleByteEncoding`() {
        val buffer = PacketBuffer(Unpooled.buffer())
        StackCountCodec.writeCount(buffer, 64)
        assertEquals(64, StackCountCodec.readCount(buffer))
    }

    @Test
    fun `countGt64_shouldUseMagicAndIntEncoding`() {
        val buffer = PacketBuffer(Unpooled.buffer())
        StackCountCodec.writeCount(buffer, 4096)
        assertEquals(4096, StackCountCodec.readCount(buffer))
    }
}
