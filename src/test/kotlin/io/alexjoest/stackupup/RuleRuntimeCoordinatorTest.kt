package io.alexjoest.stackupup

import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
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
