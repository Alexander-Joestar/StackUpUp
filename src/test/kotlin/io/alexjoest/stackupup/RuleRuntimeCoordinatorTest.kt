package io.alexjoest.stackupup

import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.RuleFileExampleTemplate
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import io.alexjoest.stackupup.rules.io.RuleSourceLocator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RuleRuntimeCoordinatorTest {
    @Test
    fun reload_shouldNotRefreshExampleFiles() {
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
            assertSame(RuleRuntime.currentSnapshot(), report.snapshot)
            assertFalse(File(rulesDir, StackUpUpIds.EXAMPLE_RULES_FILE_NAME).exists())
            assertFalse(File(rulesDir, StackUpUpIds.EXAMPLE_MARKDOWN_RULES_FILE_NAME).exists())
        } finally {
            RuleFileLocator.resetForTests()
        }
    }

    @Test
    fun syncExampleFiles_shouldRefreshExampleFiles() {
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
    fun reload_shouldCacheFailureReportWithoutPublishingRuntimeWhenLoadFails() {
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
    fun `含非法行的规则文件重载后不替换运行时快照与矿辞索引且报告为错误报告`() {
        val tempDir = createTempDirectory("stackupup-runtime-partial-error").toFile()
        val configDir = File(tempDir, "config").apply { mkdirs() }
        val rulesDir = File(configDir, StackUpUpIds.RULES_DIRECTORY_NAME).apply { mkdirs() }
        File(rulesDir, StackUpUpIds.RULES_FILE_NAME).writeText(
            "item = minecraft:egg -> 512\nnot a rule\n",
            Charsets.UTF_8,
        )
        val previousSnapshot = RuleRuntime.currentSnapshot()
        val previousIndex = RuleRuntime.oreDictIndex()
        val worldDir = File(tempDir, "world").apply { mkdirs() }

        RuleFileLocator.setConfigDirectory(configDir)
        RuleSourceLocator.setWorldDirectoryForTests(worldDir)

        try {
            val report = RuleRuntimeCoordinator.reload(enableDslRules = true)

            assertEquals(1, report.snapshot.rules.size)
            assertTrue(report.errors.isNotEmpty())
            assertSame(report, RuleRuntimeCoordinator.lastReport())
            assertSame(report.errors, RuleRuntimeCoordinator.lastReport().errors)
            assertSame(previousSnapshot, RuleRuntime.currentSnapshot())
            assertSame(previousIndex, RuleRuntime.oreDictIndex())
            assertFalse(report.snapshot === RuleRuntime.currentSnapshot())
        } finally {
            RuleSourceLocator.setWorldDirectoryForTests(null)
            RuleFileLocator.resetForTests()
        }
    }

    @Test
    fun `仅含告警的规则文件重载后照常发布运行时快照`() {
        val tempDir = createTempDirectory("stackupup-runtime-warnings-only").toFile()
        val configDir = File(tempDir, "config").apply { mkdirs() }
        val rulesDir = File(configDir, StackUpUpIds.RULES_DIRECTORY_NAME).apply { mkdirs() }
        val rulesFile = File(rulesDir, StackUpUpIds.RULES_FILE_NAME)
            .apply { writeText("item = minecraft:egg -> 500000\n", Charsets.UTF_8) }
        val previousSnapshot = RuleRuntime.currentSnapshot()
        val previousIndex = RuleRuntime.oreDictIndex()
        val previousMaxStackSize = StackUpUpConfig.maxStackSize

        RuleFileLocator.setConfigDirectory(configDir)
        RuleSourceLocator.setWorldDirectoryForTests(File(tempDir, "world").apply { mkdirs() })
        // 500000 超出本期上限 10240 -> 只产生 clamp 告警，不产生错误。
        StackUpUpConfig.maxStackSize = 10240

        try {
            val report = RuleRuntimeCoordinator.reload(enableDslRules = true)

            assertTrue(report.errors.isEmpty())
            assertEquals(1, report.warnings.size)
            assertEquals(RuleMessageKey.RULE_LIMIT_CLAMP.translationKey, report.warnings.single().translationKey)
            assertEquals(1, report.snapshot.rules.size)
            assertEquals(rulesFile.absolutePath, report.file.absolutePath)
            // warnings-only 必须放行：报告、运行时快照与服务实例一起发布，不被告警拦下。
            assertEquals(report, RuleRuntimeCoordinator.lastReport())
            assertSame(report.snapshot, RuleRuntime.currentSnapshot())
        } finally {
            StackUpUpConfig.maxStackSize = previousMaxStackSize
            RuleRuntime.replaceRuntime(previousSnapshot, previousIndex)
            RuleSourceLocator.setWorldDirectoryForTests(null)
            RuleFileLocator.resetForTests()
        }
    }

    @Test
    fun replaceRuntime_shouldPublishSnapshotOreIndexAndLimitServiceTogether() {
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

    @Test
    fun oreDictIndexReplacement_shouldResetLimitServiceCache() {
        // T6 ORE 失效条件：矿辞索引刷新（replaceOreDictIndex）→ replaceRuntime →
        // 整体换新 StackLimitService 实例，解析缓存随之清空（机制在代码中，不是注释约定）。
        val previousSnapshot = RuleRuntime.currentSnapshot()
        val previousIndex = RuleRuntime.oreDictIndex()
        try {
            val snapshot = RuleSnapshot(
                version = 50L,
                rules = listOf(RuleCompiler.compileLine("ore = ingotSteel -> 32", 1)),
            )
            RuleRuntime.replaceRuntime(snapshot, OreDictIndex({ _, _ -> setOf("ingotSteel") }))
            val firstService = RuleRuntime.limitService()
            firstService.resolve(
                StackContext(
                    itemId = "gregtech:meta_ingot",
                    modId = "gregtech",
                    metadata = 0,
                    type = "item",
                    baseLimit = 64,
                    oreNames = RuleRuntime.oreDictIndex().getOreNames("gregtech:meta_ingot", 0),
                ),
            )
            assertEquals(1, firstService.debugResolvedCacheSize())

            // 索引内容变化（不再有 ingotSteel）：刷新索引后旧缓存不得残留。
            RuleRuntime.replaceOreDictIndex(OreDictIndex({ _, _ -> emptySet() }))
            val secondService = RuleRuntime.limitService()
            assertFalse(firstService === secondService)
            assertEquals(0, secondService.debugResolvedCacheSize())
            assertEquals(
                64,
                secondService.resolve(
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
