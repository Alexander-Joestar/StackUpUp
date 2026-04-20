package io.alexjoest.stackupup.rules.io

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleReloadPipelineTest {
    @Test
    fun `规则重载不应再发出兼容上限提醒`() {
        val tempDir = Files.createTempDirectory("stackupup-rule-reload-test")
        val rulesFile = tempDir.resolve("main.su").toFile().apply {
            writeText("item = minecraft:egg -> 2048\n")
        }

        val report = RuleReloadPipeline.loadDslRules(rulesFile, listOf(rulesFile)).toReport()

        assertTrue(report.warnings.isEmpty(), "移除公开兼容上限配置后，不应再产生额外规则告警。")
    }
}
