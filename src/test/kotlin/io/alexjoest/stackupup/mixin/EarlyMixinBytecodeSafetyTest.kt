package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EarlyMixinBytecodeSafetyTest {
    @Test
    fun `静态注入 mixin 不应生成 Kotlin Companion 字段`() {
        val targets =
            listOf(
                "io.alexjoest.stackupup.mixin.early.InventoryHelperMixin",
                "io.alexjoest.stackupup.mixin.early.PacketUtilMixin"
            )

        for (target in targets) {
            val fields = Class.forName(target).declaredFields.map { it.name }
            assertFalse(
                "Companion" in fields,
                "Mixin $target 不应携带 Companion 字段，当前字段: $fields"
            )
        }
    }

    @Test
    fun `InventoryHelperMixin 不应再依赖 Kotlin splice helper`() {
        val mixinClass = Class.forName("io.alexjoest.stackupup.mixin.early.InventoryHelperMixin")
        val classBytes = requireNotNull(mixinClass.getResourceAsStream("InventoryHelperMixin.class")) {
            "无法读取 InventoryHelperMixin.class"
        }.use { it.readBytes() }

        assertFalse(
            classBytes.containsAscii("io/alexjoest/stackupup/core/InventoryHelperPerformanceSplice"),
            "InventoryHelperMixin 不应再引用 Kotlin splice helper"
        )
        assertNull(
            ClassLoader.getSystemResource("io/alexjoest/stackupup/core/InventoryHelperPerformanceSplice.class"),
            "InventoryHelperPerformanceSplice 应已从主线删除"
        )
    }

    private fun ByteArray.containsAscii(value: String): Boolean {
        if (isEmpty()) {
            return false
        }

        val target = value.encodeToByteArray()
        val lastIndex = size - target.size
        if (lastIndex < 0) {
            return false
        }

        for (index in 0..lastIndex) {
            var matches = true
            for (offset in target.indices) {
                if (this[index + offset] != target[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                return true
            }
        }
        return false
    }
}
