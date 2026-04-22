package io.alexjoest.stackupup

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import io.alexjoest.stackupup.rules.io.RuleSourceLocator

class RuleRuntimeCoordinatorTest {
    @Test
    fun `禁用 DSL 规则时应返回空快照并刷新最后报告`() {
        val tempDir = createTempDirectory("stackupup-runtime-disabled").toFile()
        val configDir = File(tempDir, "config").apply { mkdirs() }
        val rulesDir = File(configDir, StackUpUpIds.RULES_DIRECTORY_NAME).apply { mkdirs() }
        val rulesFile = File(rulesDir, StackUpUpIds.RULES_FILE_NAME).apply {
            writeText("item = minecraft:egg -> 512", Charsets.UTF_8)
        }

        RuleFileLocator.setConfigDirectory(configDir)

        try {
            val report = RuleRuntimeCoordinator.reload(enableDslRules = false)

            assertEquals(rulesFile.absolutePath, report.file.absolutePath)
            assertEquals(0, report.snapshot.rules.size)
            assertEquals(report, RuleRuntimeCoordinator.lastReport())
            assertEquals(0, RuleRuntime.currentSnapshot().rules.size)
        } finally {
            RuleFileLocator.resetForTests()
        }
    }

    @Test
    fun `写入世界规则后应立即重载并更新快照`() {
        val tempDir = createTempDirectory("stackupup-runtime-coordinator").toFile()
        val configDir = File(tempDir, "config").apply { mkdirs() }
        val rulesDir = File(configDir, StackUpUpIds.RULES_DIRECTORY_NAME).apply { mkdirs() }
        File(rulesDir, StackUpUpIds.RULES_FILE_NAME).writeText("", Charsets.UTF_8)

        val worldDir = File(tempDir, "saves/demo").apply { mkdirs() }
        RuleFileLocator.setConfigDirectory(configDir)
        RuleSourceLocator.setWorldDirectoryForTests(worldDir)

        try {
            assertEquals(
                true,
                RuleRuntimeCoordinator.persistWorldRules(
                    sourceId = "tests.runtime",
                    lines = listOf("item = minecraft:egg -> 512")
                )
            )

            val worldFile = RuleRuntimeCoordinator.getWorldRulesFile()
            assertNotNull(worldFile)
            assertEquals(true, requireNotNull(worldFile).exists())
            assertEquals(1, RuleRuntime.currentSnapshot().rules.size)
            assertEquals(
                listOf("item = minecraft:egg -> 512"),
                requireNotNull(worldFile).readLines(Charsets.UTF_8)
                    .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            )
        } finally {
            RuleFileLocator.resetForTests()
            RuleSourceLocator.setWorldDirectoryForTests(null)
        }
    }
}
