package io.alexjoest.stackupup.dev

import net.minecraft.init.Bootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.StackLimitHooks

class DevCompatProbeItemsTest {
    @Test
    fun `refined storage 终端探针物品应暴露兼容上限`() {
        Bootstrap.register()
        val stack = DevCompatProbeItems.createGridExtractProbeStack()
        assertEquals(StackLimitHooks.getCompatibilityStackSize(), stack.item.getItemStackLimit(stack))
    }
}


