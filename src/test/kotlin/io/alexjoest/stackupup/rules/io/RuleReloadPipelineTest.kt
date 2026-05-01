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

        val previous = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 10240
        val report = try {
            RuleReloadPipeline.loadDslRules(rulesFile, listOf(rulesFile))
        } finally {
            StackUpUpConfig.maxStackSize = previous
        }

        assertTrue(report.warnings.isEmpty(), "合法规则不应产生额外规则告警。")
    }

    @Test
    fun `reload_shouldWarnOnExcessiveSetRule`() {
        val tempDir = Files.createTempDirectory("stackupup-rule-reload-clamp-test")
        val rulesFile = tempDir.resolve("main.su").toFile().apply {
            writeText("item = minecraft:egg -> 500000\n")
        }

        val previous = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 10240
        val report = try {
            RuleReloadPipeline.loadDslRules(rulesFile, listOf(rulesFile))
        } finally {
            StackUpUpConfig.maxStackSize = previous
        }

        assertEquals(1, report.warnings.size)
        assertEquals(StackUpUpIds.RULE_LIMIT_CLAMP_KEY, report.warnings.single().translationKey)
        assertEquals(listOf(1, 10240), report.warnings.single().args)
    }
}
