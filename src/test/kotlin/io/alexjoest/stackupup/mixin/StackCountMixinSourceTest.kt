package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class StackCountMixinSourceTest {
    @Test
    fun packetBufferMixinSource_shouldPreserveVanillaFieldOrderAndShareTagPath() {
        val source = readSource("src/main/java/io/alexjoest/stackupup/mixin/early/PacketBufferMixin.java")

        assertTrue(source.contains("@Mixin(PacketBuffer.class)"))
        assertTrue(source.contains("method = \"readItemStack()Lnet/minecraft/item/ItemStack;\""))
        assertTrue(
            source.contains(
                "method = \"writeItemStack(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/network/PacketBuffer;\"",
            ),
        )
        assertInOrder(
            source,
            "int id = buffer.readShort();",
            "if (id < 0) {",
            "int count = StackCountCodec.readCount(buffer);",
            "int damage = buffer.readShort();",
            "stack.getItem().readNBTShareTag(stack, buffer.readCompoundTag());",
        )
        assertInOrder(
            source,
            "if (stack.isEmpty()) {",
            "buffer.writeShort(-1);",
            "buffer.writeShort(Item.getIdFromItem(stack.getItem()));",
            "StackCountCodec.writeCount(buffer, stack.getCount());",
            "buffer.writeShort(stack.getMetadata());",
            "buffer.writeCompoundTag(tag);",
        )
        assertTrue(source.contains("stack.getItem().getNBTShareTag(stack)"))
    }

    @Test
    fun packetUtilMixinSource_shouldPreserveClientToServerTagPath() {
        val source = readSource("src/main/java/io/alexjoest/stackupup/mixin/early/PacketUtilMixin.java")

        assertTrue(source.contains("@Mixin(PacketUtil.class)"))
        assertTrue(
            source.contains(
                "method = \"writeItemStackFromClientToServer(Lnet/minecraft/network/PacketBuffer;Lnet/minecraft/item/ItemStack;)V\"",
            ),
        )
        assertTrue(source.contains("remap = false"))
        assertInOrder(
            source,
            "if (stack.isEmpty()) {",
            "buffer.writeShort(-1);",
            "buffer.writeShort(Item.getIdFromItem(stack.getItem()));",
            "StackCountCodec.writeCount(buffer, stack.getCount());",
            "buffer.writeShort(stack.getMetadata());",
            "buffer.writeCompoundTag(tag);",
        )
        assertTrue(source.contains("tag = stack.getTagCompound();"))
    }

    @Test
    fun itemStackNbtMixinSource_shouldReadAndWriteOnlyIntegerCount() {
        val source = readSource("src/main/java/io/alexjoest/stackupup/mixin/early/ItemStackNbtMixin.java")

        assertTrue(source.contains("@Mixin(ItemStack.class)"))
        assertTrue(source.contains("method = \"<init>(Lnet/minecraft/nbt/NBTTagCompound;)V\""))
        assertTrue(source.contains("target = \"Lnet/minecraft/item/ItemStack;itemDamage:I\""))
        assertTrue(source.contains("shift = At.Shift.AFTER"))
        assertTrue(source.contains("opcode = Opcodes.PUTFIELD"))
        assertTrue(source.contains("compound.getInteger(\"Count\")"))
        assertTrue(
            source.contains(
                "writeToNBT(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
            ),
        )
        assertTrue(source.contains("at = @At(\"RETURN\")"))
        assertTrue(source.contains("cir.getReturnValue().setInteger(\"Count\", this.stackSize);"))
        assertFalse(source.contains("cancellable = true"))
        assertFalse(source.contains("setString("))
        assertFalse(source.contains("setShort("))
        assertFalse(source.contains("setTag("))
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
