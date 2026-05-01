package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.limit.StackLimitService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class MarkdownRuleSourceTest {
    private var previousMaxStackSize: Int = StackUpUpConfig.maxStackSize

    @AfterEach
    fun restoreMaxStackSize() {
        StackUpUpConfig.maxStackSize = previousMaxStackSize
    }

    @Test
    fun `shouldCompileOnlyEnabledMarkdownRuleBlocks`() {
        previousMaxStackSize = StackUpUpConfig.maxStackSize
        val result = MarkdownRuleSource.fromLines(
            listOf(
                "# state",
                "- phase1 = true",
                "",
                "# rules",
                "## state(\"phase1\") && modLoaded(\"storagenetwork\")",
                "```stackupup",
                "item = minecraft:egg -> 128",
                "```",
                "## state(\"phase1\") && modLoaded(\"missing\")",
                "```stackupup",
                "item = minecraft:snowball -> 16",
                "```",
            ),
            gateContext = RuleGateContext(loadedMods = setOf("storagenetwork")),
        )

        val previousMaxStackSize = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 10240
        assertEquals(1, result.snapshot.rules.size, "compiled rules=${result.snapshot.rules}")
        assertEquals("item = minecraft:egg -> 128", result.snapshot.rules.single().sourceLine)
        val service = StackLimitService(result.snapshot)
        val context = StackContext("minecraft:egg", "minecraft", 0, "item", 16, emptySet())

        try {
            assertEquals(128, service.resolve(context))
            assertTrue(result.errors.isEmpty())
        } finally {
            StackUpUpConfig.maxStackSize = previousMaxStackSize
        }
    }

    @Test
    fun `shouldLoadMultipleMarkdownFilesInOrder`() {
        val tempDir = createTempDirectory("stackupup-markdown-multi-source").toFile()
        val pack = File(tempDir, "pack.su.md").apply {
            writeText(
                """
                # rules
                ```stackupup
                item = minecraft:egg -> 64
                ```
                """.trimIndent() + System.lineSeparator(),
                Charsets.UTF_8,
            )
        }
        val world = File(tempDir, "world.su.md").apply {
            writeText(
                """
                # rules
                ```stackupup
                item = minecraft:egg -> 128
                ```
                """.trimIndent() + System.lineSeparator(),
                Charsets.UTF_8,
            )
        }

        previousMaxStackSize = StackUpUpConfig.maxStackSize
        StackUpUpConfig.maxStackSize = 10240

        val result = MarkdownRuleSource.fromFiles(listOf(pack, world))
        val service = StackLimitService(result.snapshot)
        val context = StackContext("minecraft:egg", "minecraft", 0, "item", 16, emptySet())

        try {
            assertEquals(128, service.resolve(context))
            assertTrue(result.errors.isEmpty())
        } finally {
            StackUpUpConfig.maxStackSize = previousMaxStackSize
        }
    }
}
