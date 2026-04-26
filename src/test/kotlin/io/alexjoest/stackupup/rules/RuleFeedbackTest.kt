package io.alexjoest.stackupup.rules

import java.io.File
import net.minecraft.util.text.TextComponentTranslation
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.RuleComplexityWarning
import io.alexjoest.stackupup.rules.io.RuleFeedback
import io.alexjoest.stackupup.rules.io.RuleLineLoader
import io.alexjoest.stackupup.rules.io.RuleReloadReport

class RuleFeedbackTest {
    @Test
    fun `规则加载错误应保留翻译键与嵌套参数到聊天组件边界`() {
        val report = RuleReloadReport(
            file = File("run/config/stackupup/main.su"),
            snapshot = RuleSnapshot(version = 1L, rules = emptyList()),
            errors = listOf(
                RuleLineLoader.RuleLineInput("broken", 7, "pack.su")
                    .formatError(
                        LocalizedRuleException(
                            RuleMessages.message(
                                RuleMessageKey.UNSUPPORTED_FIELD,
                                "mystery"
                            )
                        )
                    )
            ),
            warnings = emptyList()
        )
        val emitted = mutableListOf<TextComponentTranslation>()

        RuleFeedback.emitReloadErrors(report) { component ->
            emitted += component as TextComponentTranslation
        }

        assertEquals(2, emitted.size)
        assertEquals(StackUpUpIds.RULE_RELOAD_ERROR_PREFIX_KEY, emitted[0].key)
        assertEquals(RuleMessageKey.LOAD_FAILED_WITH_SOURCE.translationKey, emitted[1].key)
        assertEquals("pack.su", emitted[1].formatArgs[0])
        assertEquals(7, emitted[1].formatArgs[1])
        val nested = emitted[1].formatArgs[2] as TextComponentTranslation
        assertEquals(RuleMessageKey.UNSUPPORTED_FIELD.translationKey, nested.key)
        assertArrayEquals(arrayOf("mystery"), nested.formatArgs)
    }

    @Test
    fun `复杂度提醒应保留翻译键与参数`() {
        val previous = StackUpUpConfig.ruleComplexityWarnings
        StackUpUpConfig.ruleComplexityWarnings = true
        try {
            val report = RuleReloadReport(
                file = File("run/config/stackupup/main.su"),
                snapshot = RuleSnapshot(version = 1L, rules = emptyList()),
                errors = emptyList(),
                warnings = listOf(RuleComplexityWarning(StackUpUpIds.RULE_COMPLEXITY_RULE_COUNT_KEY, listOf(80)))
            )
            val emitted = mutableListOf<TextComponentTranslation>()

            RuleFeedback.emitWarnings(report) { component ->
                emitted += component as TextComponentTranslation
            }

            assertEquals(2, emitted.size)
            assertEquals(StackUpUpIds.RULE_COMPLEXITY_PREFIX_KEY, emitted[0].key)
            assertEquals(StackUpUpIds.RULE_COMPLEXITY_RULE_COUNT_KEY, emitted[1].key)
            assertArrayEquals(arrayOf(80), emitted[1].formatArgs)
        } finally {
            StackUpUpConfig.ruleComplexityWarnings = previous
        }
    }
}
