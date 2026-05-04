package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.rules.LocalizedMessage
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.RuleFeedback
import io.alexjoest.stackupup.rules.io.RuleLineLoader
import io.alexjoest.stackupup.rules.io.RuleReloadReport
import net.minecraft.util.text.TextComponentTranslation
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class RuleFeedbackTest {
    @Test
    fun `error_shouldPreserveTranslationKeyAndArgs`() {
        val report = RuleReloadReport(
            file = File("run/config/stackupup/main.su"),
            snapshot = RuleSnapshot(version = 1L, rules = emptyList()),
            errors = listOf(
                RuleLineLoader.RuleLineInput("broken", 7, "pack.su")
                    .formatError(
                        LocalizedRuleException(
                            RuleMessages.message(
                                RuleMessageKey.UNSUPPORTED_FIELD,
                                "mystery",
                            ),
                        ),
                    ),
            ),
            warnings = emptyList(),
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
    fun `complexityWarning_shouldPreserveTranslationKey`() {
        val previous = StackUpUpConfig.ruleComplexityWarnings
        StackUpUpConfig.ruleComplexityWarnings = true
        try {
            val report = RuleReloadReport(
                file = File("run/config/stackupup/main.su"),
                snapshot = RuleSnapshot(version = 1L, rules = emptyList()),
                errors = emptyList(),
                warnings = listOf(LocalizedMessage(StackUpUpIds.RULE_COMPLEXITY_RULE_COUNT_KEY, listOf(80))),
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
