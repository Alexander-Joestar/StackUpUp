package io.alexjoest.stackupup.network

import io.netty.buffer.Unpooled
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

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

    @Test
    fun packetMixins_shouldKeepVanillaFieldOrderAndDistinctTagPaths() {
        val packetBufferMixin = readSource(
            "src/main/java/io/alexjoest/stackupup/mixin/early/PacketBufferMixin.java",
        )
        val packetUtilMixin = readSource(
            "src/main/java/io/alexjoest/stackupup/mixin/early/PacketUtilMixin.java",
        )

        assertInOrder(
            packetBufferMixin,
            "buffer.writeShort(Item.getIdFromItem(stack.getItem()));",
            "StackCountCodec.writeCount(buffer, stack.getCount());",
            "buffer.writeShort(stack.getMetadata());",
            "buffer.writeCompoundTag(tag);",
        )
        assertInOrder(
            packetBufferMixin,
            "int id = buffer.readShort();",
            "int count = StackCountCodec.readCount(buffer);",
            "int damage = buffer.readShort();",
            "stack.getItem().readNBTShareTag(stack, buffer.readCompoundTag());",
        )
        assertInOrder(
            packetBufferMixin,
            "if (stack.isEmpty()) {",
            "buffer.writeShort(-1);",
        )
        assertTrue(packetBufferMixin.contains("if (id < 0) {"))
        assertTrue(packetBufferMixin.contains("stack.getItem().getNBTShareTag(stack)"))

        assertInOrder(
            packetUtilMixin,
            "buffer.writeShort(Item.getIdFromItem(stack.getItem()));",
            "StackCountCodec.writeCount(buffer, stack.getCount());",
            "buffer.writeShort(stack.getMetadata());",
            "buffer.writeCompoundTag(tag);",
        )
        assertInOrder(
            packetUtilMixin,
            "if (stack.isEmpty()) {",
            "buffer.writeShort(-1);",
        )
        assertTrue(packetUtilMixin.contains("tag = stack.getTagCompound();"))
    }

    private fun assertInOrder(source: String, vararg snippets: String) {
        var previousIndex = -1
        for (snippet in snippets) {
            val nextIndex = source.indexOf(snippet)
            assertTrue(nextIndex > previousIndex, "Expected '$snippet' after index $previousIndex")
            previousIndex = nextIndex
        }
    }

    private fun readSource(path: String): String = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
}
