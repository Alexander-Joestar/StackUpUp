package io.alexjoest.stackupup

import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RuleRuntimeCoordinatorTest {
    @Test
    fun `dslDisabled_shouldReturnEmptySnapshotAndRefresh`() {
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
}
