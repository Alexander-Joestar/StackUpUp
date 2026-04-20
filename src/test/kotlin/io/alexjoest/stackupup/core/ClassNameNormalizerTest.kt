package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClassNameNormalizerTest {
    @Test
    fun `slash 类名应归一化为 dot 类名`() {
        assertEquals(
            "a.b.C",
            toDotClassName("a/b/C")
        )
    }
}
