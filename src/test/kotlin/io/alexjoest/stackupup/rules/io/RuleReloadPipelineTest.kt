package io.alexjoest.stackupup.rules.io

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpIds

class RuleReloadPipelineTest {
    @Test
    fun `规则重载不应对合法堆叠上限产生额外告警`() {
        val tempDir = Files.createTempDirectory("stackupup-rule-reload-test")
        val rulesFile = tempDir.resolve("main.su").toFile().apply {
            writeText("item = minecraft:egg -> 2048\n")
        }

        val previous = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 10240
        val report = try {
            RuleReloadPipeline.loadDslRules(rulesFile, listOf(rulesFile)).toReport()
        } finally {
            StackUpUpConfig.maxStackSize = previous
        }

        assertTrue(report.warnings.isEmpty(), "合法规则不应产生额外规则告警。")
    }

    @Test
    fun `规则重载应警告明确超出全局最大堆叠上限的 set 规则`() {
        val tempDir = Files.createTempDirectory("stackupup-rule-reload-clamp-test")
        val rulesFile = tempDir.resolve("main.su").toFile().apply {
            writeText("item = minecraft:egg -> 500000\n")
        }

        val previous = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 10240
        val report = try {
            RuleReloadPipeline.loadDslRules(rulesFile, listOf(rulesFile)).toReport()
        } finally {
            StackUpUpConfig.maxStackSize = previous
        }

        assertEquals(1, report.warnings.size)
        assertEquals(StackUpUpIds.RULE_LIMIT_CLAMP_KEY, report.warnings.single().translationKey)
        assertEquals(listOf(1, 10240), report.warnings.single().args)
    }
}
