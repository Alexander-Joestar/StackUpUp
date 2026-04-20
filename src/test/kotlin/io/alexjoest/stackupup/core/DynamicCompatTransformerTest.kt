package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DynamicCompatTransformerTest {
    private val transformer = DynamicCompatTransformer()

    @Test
    fun `basicClass 为空时应直接返回空而不是抛异常`() {
        assertNull(transformer.transform(null, "net/minecraft/inventory/Slot", null))
    }

    @Test
    fun `transformedName 为空时应回退使用 name`() {
        val bytes = classBytes("io.alexjoest.stackupup.core.DynamicCompatTransformerTest")
        assertArrayEquals(
            bytes,
            transformer.transform(
                "io/alexjoest/stackupup/core/DynamicCompatTransformerTest",
                null,
                bytes
            )
        )
    }

    @Test
    fun `固定目标不应被 dynamic ASM 改写`() {
        val bytes = classBytes("net.minecraftforge.items.ItemStackHandler")
        assertArrayEquals(
            bytes,
            transformer.transform(
                null,
                "net/minecraftforge/items/ItemStackHandler",
                bytes
            )
        )
    }

    private fun classBytes(className: String): ByteArray {
        val resourcePath = className.replace('.', '/') + ".class"
        return requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "无法读取类字节码: $className"
        }.use { it.readBytes() }
    }
}
