package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DynamicCompatMethodProbeTest {
    @Test
    fun `inventory 方法应识别为 inventory profile`() {
        assertEquals(
            DynamicCompatTargetProfile.INVENTORY,
            DynamicCompatMethodProbe.detectProfiles(classBytes("io.alexjoest.stackupup.core.TestInventoryOverride"))
        )
    }

    @Test
    fun `item handler 方法应识别为 item handler profile`() {
        assertEquals(
            DynamicCompatTargetProfile.ITEM_HANDLER,
            DynamicCompatMethodProbe.detectProfiles(classBytes("net.minecraftforge.items.ItemStackHandler"))
        )
    }

    @Test
    fun `slot 方法应识别为 slot profile`() {
        assertEquals(
            DynamicCompatTargetProfile.SLOT,
            DynamicCompatMethodProbe.detectProfiles(classBytes("net.minecraft.inventory.Slot"))
        )
    }

    private fun classBytes(className: String): ByteArray {
        val resourcePath = className.replace('.', '/') + ".class"
        return requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "无法读取类字节码: $className"
        }.use { it.readBytes() }
    }
}
