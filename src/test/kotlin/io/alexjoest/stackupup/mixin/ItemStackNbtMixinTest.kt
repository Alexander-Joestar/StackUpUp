package io.alexjoest.stackupup.mixin

import net.minecraft.nbt.NBTTagCompound
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class ItemStackNbtMixinTest {
    @Test
    fun integerCountField_shouldPreserveSignedIntBoundariesAndNeighborFields() {
        val stackTag = NBTTagCompound().apply { setString("name", "test") }
        val capabilities = NBTTagCompound().apply { setInteger("energy", 7) }
        val nbt = NBTTagCompound().apply {
            setString("id", "minecraft:stone")
            setShort("Damage", 0x1234.toShort())
            setTag("tag", stackTag)
            setTag("ForgeCaps", capabilities)
        }

        for (count in listOf(Int.MIN_VALUE, -1, 0, 1, 64, 65, 127, 128, Short.MAX_VALUE.toInt() + 1, Int.MAX_VALUE)) {
            nbt.setInteger("Count", count)

            assertEquals(count, nbt.getInteger("Count"))
            assertEquals("minecraft:stone", nbt.getString("id"))
            assertEquals(0x1234.toShort(), nbt.getShort("Damage"))
            assertEquals("test", nbt.getCompoundTag("tag").getString("name"))
            assertEquals(7, nbt.getCompoundTag("ForgeCaps").getInteger("energy"))
        }
    }

    @Test
    fun itemStackNbtMixin_shouldReadIntegerCountAndUpdateVanillaWriterResult() {
        val source = readSource("src/main/java/io/alexjoest/stackupup/mixin/early/ItemStackNbtMixin.java")

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

    private fun readSource(path: String): String = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
}
