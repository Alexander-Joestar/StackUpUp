package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DynamicCompatMethodProbeTest {
    @Test
    fun `inventoryMethod_shouldIdentifyAsInventory`() {
        assertEquals(
            DynamicCompatTargetProfile.INVENTORY,
            DynamicCompatMethodProbe.detectProfiles(classBytes("io.alexjoest.stackupup.core.TestInventoryOverride")),
        )
    }

    @Test
    fun `itemHandlerMethod_shouldIdentifyAsItemHandler`() {
        assertEquals(
            DynamicCompatTargetProfile.ITEM_HANDLER,
            DynamicCompatMethodProbe.detectProfiles(classBytes("net.minecraftforge.items.ItemStackHandler")),
        )
    }

    @Test
    fun `slotMethod_shouldIdentifyAsSlot`() {
        assertEquals(
            DynamicCompatTargetProfile.SLOT,
            DynamicCompatMethodProbe.detectProfiles(classBytes("net.minecraft.inventory.Slot")),
        )
    }

    private fun classBytes(className: String): ByteArray {
        val resourcePath = className.replace('.', '/') + ".class"
        return requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "无法读取类字节码: $className"
        }.use { it.readBytes() }
    }
}
