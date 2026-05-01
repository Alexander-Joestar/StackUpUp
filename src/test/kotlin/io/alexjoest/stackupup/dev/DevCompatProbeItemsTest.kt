package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackLimitHooks
import net.minecraft.init.Bootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevCompatProbeItemsTest {
    @Test
    fun `rsTerminalProbe_shouldExposeCompatLimit`() {
        Bootstrap.register()
        val stack = DevCompatProbeItems.createGridExtractProbeStack()
        assertEquals(StackLimitHooks.getCompatibilityStackSize(), stack.item.getItemStackLimit(stack))
    }
}
