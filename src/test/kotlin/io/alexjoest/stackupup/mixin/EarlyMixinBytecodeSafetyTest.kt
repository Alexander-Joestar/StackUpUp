package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertFalse
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
}
