package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RuleReloadPipelineTest {
    @Test
    fun `reload_shouldNotWarnOnValidLimits`() {
        val tempDir = Files.createTempDirectory("stackupup-rule-reload-test")
        val rulesFile = tempDir.resolve("main.su").toFile().apply {
            writeText("item = minecraft:egg -> 2048\n")
        }

        val previous = StackUpUpConfig.activeMaxStackSize
        StackUpUpConfig.general.maxStackSize = 10240
        StackUpUpConfig.activeMaxStackSize = 10240
        val report = try {
            RuleReloadPipeline.loadDslRules(rulesFile, listOf(rulesFile))
        } finally {
            StackUpUpConfig.activeMaxStackSize = previous
        }

        assertTrue(report.warnings.isEmpty(), "合法规则不应产生额外规则告警。")
    }

    @Test
    fun `reload_shouldWarnOnExcessiveSetRule`() {
        val tempDir = Files.createTempDirectory("stackupup-rule-reload-clamp-test")
        val rulesFile = tempDir.resolve("main.su").toFile().apply {
            writeText("item = minecraft:egg -> 500000\n")
        }

        val previous = StackUpUpConfig.activeMaxStackSize
        StackUpUpConfig.general.maxStackSize = 10240
        StackUpUpConfig.activeMaxStackSize = 10240
        val report = try {
            RuleReloadPipeline.loadDslRules(rulesFile, listOf(rulesFile))
        } finally {
            StackUpUpConfig.activeMaxStackSize = previous
        }

        assertEquals(1, report.warnings.size)
        assertEquals(StackUpUpIds.RULE_LIMIT_CLAMP_KEY, report.warnings.single().translationKey)
        assertEquals(listOf(1, 10240), report.warnings.single().args)
    }

    @Test
    fun `reload_shouldKeepMarkdownRuleErrorsBeforeStateErrors`() {
        val tempDir = Files.createTempDirectory("stackupup-rule-reload-markdown-error-order-test")
        val primaryRulesFile = tempDir.resolve("main.su").toFile()
        val markdownFile = tempDir.resolve("main.su.md").toFile().apply {
            writeText(
                """
                # state
                - phase1 = maybe
                # rules
                ```stackupup
                item minecraft:egg -> 64
                ```
                """.trimIndent() + System.lineSeparator(),
                Charsets.UTF_8,
            )
        }

        val report = RuleReloadPipeline.loadDslRules(primaryRulesFile, listOf(markdownFile))

        assertEquals(2, report.errors.size)
        assertTrue(report.errors[0].format().contains("[main.su.md]"))
        assertTrue(report.errors[0].format().contains("failed to load"))
        assertTrue(report.errors[1].format().startsWith("[state] "))
        assertTrue(report.errors[1].format().contains("phase1 = maybe"))
    }
}
