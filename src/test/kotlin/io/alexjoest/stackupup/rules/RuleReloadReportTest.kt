package io.alexjoest.stackupup.rules

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.RuleComplexityWarning
import io.alexjoest.stackupup.rules.io.RuleReloadReport

class RuleReloadReportTest {
    @Test
    fun `无错误且无复杂度提醒时应视为干净重载`() {
        val report = RuleReloadReport(
            file = File("run/config/stackupup/main.su"),
            snapshot = RuleSnapshot(version = 1L, rules = emptyList()),
            errors = emptyList(),
            warnings = emptyList()
        )

        assertTrue(report.isClean)
    }

    @Test
    fun `应当保留加载错误与复杂度提醒`() {
        val report = RuleReloadReport(
            file = File("run/config/stackupup/main.su"),
            snapshot = RuleSnapshot(version = 1L, rules = emptyList()),
            errors = listOf("第 1 行加载失败"),
            warnings = listOf(RuleComplexityWarning("message.stackupup.rule_complexity.rule_count", emptyList()))
        )

        assertEquals(listOf("第 1 行加载失败"), report.errors)
        assertEquals(listOf("message.stackupup.rule_complexity.rule_count"), report.complexityWarnings)
    }
}
