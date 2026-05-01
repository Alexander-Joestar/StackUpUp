package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.rules.LocalizedMessage
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.RuleComplexityWarning
import io.alexjoest.stackupup.rules.io.RuleReloadReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RuleReloadReportTest {
    @Test
    fun `noErrorsNoWarnings_shouldBeClean`() {
        val report = RuleReloadReport(
            file = File("run/config/stackupup/main.su"),
            snapshot = RuleSnapshot(version = 1L, rules = emptyList()),
            errors = emptyList(),
            warnings = emptyList(),
        )

        assertTrue(report.isClean)
    }

    @Test
    fun `shouldPreserveErrorsAndWarnings`() {
        val report = RuleReloadReport(
            file = File("run/config/stackupup/main.su"),
            snapshot = RuleSnapshot(version = 1L, rules = emptyList()),
            errors = listOf(LocalizedMessage("message.stackupup.rule_error.load_failed", listOf(1, "broken"))),
            warnings = listOf(RuleComplexityWarning(StackUpUpIds.RULE_COMPLEXITY_RULE_COUNT_KEY, emptyList())),
        )

        assertEquals(listOf("Line 1 failed to load: broken"), report.errors.map { it.format() })
        assertEquals(listOf(StackUpUpIds.RULE_COMPLEXITY_RULE_COUNT_KEY), report.warnings.map { it.translationKey })
    }
}
