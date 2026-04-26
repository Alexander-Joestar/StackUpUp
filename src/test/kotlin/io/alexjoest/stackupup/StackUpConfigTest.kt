package io.alexjoest.stackupup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StackUpUpConfigTest {
    @Test
    fun `配置门面应继续暴露扁平运行时访问`() {
        val previousEnableDslRules = StackUpUpConfig.general.enableDslRules
        val previousTooltipStackDisplayMode = StackUpUpConfig.general.tooltipStackDisplayMode
        val previousMaxStackSize = StackUpUpConfig.general.maxStackSize
        val previousActiveMaxStackSize = StackUpUpConfig.activeMaxStackSize
        val previousFontScaleMinimum = StackUpUpConfig.client.fontScaleMinimum
        val previousFontScaleMaximum = StackUpUpConfig.client.fontScaleMaximum

        try {
            StackUpUpConfig.general.enableDslRules = false
            StackUpUpConfig.general.tooltipStackDisplayMode = TooltipStackDisplayMode.ALWAYS
            StackUpUpConfig.general.maxStackSize = 65536
            StackUpUpConfig.client.fontScaleMinimum = 0.4
            StackUpUpConfig.client.fontScaleMaximum = 0.4

            StackUpUpConfig.applyRuntimeValues()

            assertFalse(StackUpUpConfig.enableDslRules)
            assertEquals(TooltipStackDisplayMode.ALWAYS, StackUpUpConfig.tooltipStackDisplayMode)
            assertEquals(previousActiveMaxStackSize, StackUpUpConfig.maxStackSize)
            assertEquals(0.4f, StackUpUpConfig.lowestScaleDown)
            assertEquals(0.4f, StackUpUpConfig.highestScaleDown)
            assertTrue(StackUpUpConfig.equalScaleDown)

            StackUpUpConfig.applyReloadControlledValues()

            assertEquals(65536, StackUpUpConfig.maxStackSize)
        } finally {
            StackUpUpConfig.general.enableDslRules = previousEnableDslRules
            StackUpUpConfig.general.tooltipStackDisplayMode = previousTooltipStackDisplayMode
            StackUpUpConfig.general.maxStackSize = previousMaxStackSize
            StackUpUpConfig.client.fontScaleMinimum = previousFontScaleMinimum
            StackUpUpConfig.client.fontScaleMaximum = previousFontScaleMaximum
            StackUpUpConfig.activeMaxStackSize = previousActiveMaxStackSize
            StackUpUpConfig.applyRuntimeValues()
        }
    }

    @Test
    fun `公开标识应使用 stackupup`() {
        assertEquals(StackUpUpIds.MOD_ID, StackUpUp.MOD_ID)
        assertEquals(StackUpUpIds.PUBLIC_ID, StackUpUp.PUBLIC_ID)
        assertEquals(StackUpUpIds.CONFIG_ID, StackUpUp.CONFIG_ID)
        assertEquals(StackUpUpIds.RULES_FILE_NAME, StackUpUp.RULES_FILE_NAME_PUBLIC)
        assertEquals(Tags.MOD_ID, StackUpUpIds.MOD_ID)
        assertEquals(Tags.MOD_NAME, StackUpUpIds.MOD_NAME)
        assertEquals(Tags.VERSION, StackUpUp.VERSION)
    }

    @Test
    fun `标识常量应集中在单一入口`() {
        assertEquals("stackupup", StackUpUpIds.MOD_ID)
        assertEquals("config.stackupup", StackUpUpIds.CONFIG_LANG_ROOT)
        assertEquals("commands.stackupup", StackUpUpIds.COMMAND_LANG_ROOT)
        assertEquals("message.stackupup", StackUpUpIds.MESSAGE_LANG_ROOT)
        assertEquals("config.stackupup.title", StackUpUpIds.CONFIG_TITLE_KEY)
        assertEquals("commands.stackupup.reload.success", StackUpUpIds.COMMAND_RELOAD_SUCCESS_KEY)
        assertEquals("message.stackupup.rule_complexity.rule_count", StackUpUpIds.RULE_COMPLEXITY_RULE_COUNT_KEY)
        assertEquals("message.stackupup.rule_complexity.rule_length", StackUpUpIds.RULE_COMPLEXITY_RULE_LENGTH_KEY)
        assertEquals("message.stackupup.rule_complexity.total_length", StackUpUpIds.RULE_COMPLEXITY_TOTAL_LENGTH_KEY)
        assertEquals("message.stackupup.rule_limit.clamp", StackUpUpIds.RULE_LIMIT_CLAMP_KEY)
        assertEquals("tooltip.stackupup.current_max", StackUpUpIds.TOOLTIP_CURRENT_MAX_KEY)
        assertEquals("io/alexjoest/stackupup/StackLimitHooks", StackUpUpIds.STACK_LIMIT_HOOKS_INTERNAL_NAME)
    }

    @Test
    fun `不应再暴露 DSL v1 旧脚本配置入口`() {
        assertThrows(NoSuchMethodException::class.java) {
            StackUpUpConfig::class.java.getMethod("getScriptingActive")
        }
        assertThrows(NoSuchFieldException::class.java) {
            StackUpUpConfig.General::class.java.getDeclaredField("enableScripting")
        }
    }
}
