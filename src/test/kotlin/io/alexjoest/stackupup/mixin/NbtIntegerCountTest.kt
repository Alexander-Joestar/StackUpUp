package io.alexjoest.stackupup.mixin

import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class NbtIntegerCountTest {
    @Test
    fun integerCountNbt_shouldRoundTripBoundaryValuesAndAdjacentVanillaFields() {
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
            val output = ByteArrayOutputStream()
            CompressedStreamTools.writeCompressed(nbt, output)
            val restored = CompressedStreamTools.readCompressed(ByteArrayInputStream(output.toByteArray()))

            assertEquals(count, restored.getInteger("Count"))
            assertEquals("minecraft:stone", restored.getString("id"))
            assertEquals(0x1234.toShort(), restored.getShort("Damage"))
            assertEquals("test", restored.getCompoundTag("tag").getString("name"))
            assertEquals(7, restored.getCompoundTag("ForgeCaps").getInteger("energy"))
        }
    }
}
