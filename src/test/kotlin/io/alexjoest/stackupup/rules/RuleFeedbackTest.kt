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
import io.alexjoest.stackupup.rules.io.RuleReloadReport

class RuleFeedbackTest {
    @Test
    fun `复杂度提醒应保留翻译键与参数`() {
        val previous = StackUpUpConfig.ruleComplexityWarnings
        StackUpUpConfig.ruleComplexityWarnings = true
        try {
            val report = RuleReloadReport(
                file = File("run/config/stackupup/main.su"),
                snapshot = RuleSnapshot(version = 1L, rules = emptyList()),
                errors = emptyList(),
                warnings = listOf(RuleComplexityWarning("message.stackupup.rule_complexity.rule_count", listOf(80)))
            )
            val emitted = mutableListOf<TextComponentTranslation>()

            RuleFeedback.emitWarnings(report) { component ->
                emitted += component as TextComponentTranslation
            }

            assertEquals(2, emitted.size)
            assertEquals(StackUpUpIds.RULE_COMPLEXITY_PREFIX_KEY, emitted[0].key)
            assertEquals("message.stackupup.rule_complexity.rule_count", emitted[1].key)
            assertArrayEquals(arrayOf(80), emitted[1].formatArgs)
        } finally {
            StackUpUpConfig.ruleComplexityWarnings = previous
        }
    }
}
