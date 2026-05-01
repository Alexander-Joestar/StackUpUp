package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.StackLimitHooks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RefinedStorageExtractHooksTest {
    @Test
    fun `default64Extract_shouldWidenToRealLimit`() {
        assertEquals(10240L, StackLimitHooks.expandDefaultExtractLimit(64L, 10240L))
    }

    @Test
    fun `nonDefaultRequest_shouldPreserveMinSemantics`() {
        assertEquals(512L, StackLimitHooks.expandDefaultExtractLimit(512L, 10240L))
        assertEquals(64L, StackLimitHooks.expandDefaultExtractLimit(512L, 64L))
    }
}
