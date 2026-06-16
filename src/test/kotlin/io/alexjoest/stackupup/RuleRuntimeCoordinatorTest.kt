package io.alexjoest.stackupup

import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.RuleFileExampleTemplate
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RuleRuntimeCoordinatorTest {
    @Test
    fun `reload_shouldNotRefreshExampleFiles`() {
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
            assertFalse(File(rulesDir, StackUpUpIds.EXAMPLE_RULES_FILE_NAME).exists())
            assertFalse(File(rulesDir, StackUpUpIds.EXAMPLE_MARKDOWN_RULES_FILE_NAME).exists())
        } finally {
            RuleFileLocator.resetForTests()
        }
    }

    @Test
    fun `syncExampleFiles_shouldRefreshExampleFiles`() {
        val tempDir = createTempDirectory("stackupup-runtime-examples").toFile()
        val configDir = File(tempDir, "config").apply { mkdirs() }
        val rulesDir = File(configDir, StackUpUpIds.RULES_DIRECTORY_NAME).apply { mkdirs() }
        File(rulesDir, StackUpUpIds.RULES_FILE_NAME).writeText("", Charsets.UTF_8)

        RuleFileLocator.setConfigDirectory(configDir)

        try {
            RuleRuntimeCoordinator.syncExampleFiles()

            val exampleFile = File(rulesDir, StackUpUpIds.EXAMPLE_RULES_FILE_NAME)
            val markdownFile = File(rulesDir, StackUpUpIds.EXAMPLE_MARKDOWN_RULES_FILE_NAME)
            assertTrue(exampleFile.exists())
            assertTrue(markdownFile.exists())
            assertEquals(RuleFileExampleTemplate.exampleContent, exampleFile.readText(Charsets.UTF_8))
            assertEquals(RuleFileExampleTemplate.markdownExampleContent, markdownFile.readText(Charsets.UTF_8))
        } finally {
            RuleFileLocator.resetForTests()
        }
    }

    @Test
    fun `reload_shouldCacheFailureReportWithoutPublishingRuntimeWhenLoadFails`() {
        val tempDir = createTempDirectory("stackupup-runtime-failure").toFile()
        val configFile = File(tempDir, "config").apply {
            writeText("not a directory", Charsets.UTF_8)
        }
        val expectedRulesFile = File(
            File(configFile, StackUpUpIds.RULES_DIRECTORY_NAME),
            StackUpUpIds.RULES_FILE_NAME,
        )
        val previousSnapshot = RuleRuntime.currentSnapshot()
        val previousIndex = RuleRuntime.oreDictIndex()

        RuleFileLocator.setConfigDirectory(configFile)

        try {
            val report = RuleRuntimeCoordinator.reload(enableDslRules = true)

            assertEquals(expectedRulesFile.absolutePath, report.file.absolutePath)
            assertEquals(0, report.snapshot.rules.size)
            assertTrue(report.errors.isNotEmpty())
            assertEquals(report, RuleRuntimeCoordinator.lastReport())
            assertSame(previousSnapshot, RuleRuntime.currentSnapshot())
            assertSame(previousIndex, RuleRuntime.oreDictIndex())
        } finally {
            RuleFileLocator.resetForTests()
        }
    }

    @Test
    fun `replaceRuntime_shouldPublishSnapshotOreIndexAndLimitServiceTogether`() {
        val previousSnapshot = RuleRuntime.currentSnapshot()
        val previousIndex = RuleRuntime.oreDictIndex()
        val snapshot = RuleSnapshot(
            version = 42L,
            rules = listOf(RuleCompiler.compileLine("ore = ingotSteel -> 32", 1)),
        )
        val index = OreDictIndex({ _, _ -> setOf("ingotSteel") })

        try {
            RuleRuntime.replaceRuntime(snapshot, index)

            assertSame(snapshot, RuleRuntime.currentSnapshot())
            assertSame(index, RuleRuntime.oreDictIndex())
            assertEquals(
                32,
                RuleRuntime.limitService().resolve(
                    StackContext(
                        itemId = "gregtech:meta_ingot",
                        modId = "gregtech",
                        metadata = 0,
                        type = "item",
                        baseLimit = 64,
                        oreNames = RuleRuntime.oreDictIndex().getOreNames("gregtech:meta_ingot", 0),
                    ),
                ),
            )
        } finally {
            RuleRuntime.replaceRuntime(previousSnapshot, previousIndex)
        }
    }
}
