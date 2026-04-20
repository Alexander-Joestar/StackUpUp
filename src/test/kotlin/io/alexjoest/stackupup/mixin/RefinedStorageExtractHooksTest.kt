package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.StackLimitHooks

class RefinedStorageExtractHooksTest {
    @Test
    fun `默认 64 提取请求应放宽到物品真实上限`() {
        assertEquals(10240L, StackLimitHooks.expandDefaultExtractLimit(64L, 10240L))
    }

    @Test
    fun `非默认请求量应保持原始最小值语义`() {
        assertEquals(512L, StackLimitHooks.expandDefaultExtractLimit(512L, 10240L))
        assertEquals(64L, StackLimitHooks.expandDefaultExtractLimit(512L, 64L))
    }
}
