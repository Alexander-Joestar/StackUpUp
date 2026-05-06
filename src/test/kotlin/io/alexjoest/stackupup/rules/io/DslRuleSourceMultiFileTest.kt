package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.limit.StackLimitService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DslRuleSourceMultiFileTest {
    private var previousMaxStackSize: Int = 10240

    @BeforeEach
    fun setUpMaxStackSize() {
        previousMaxStackSize = StackUpUpConfig.activeMaxStackSize
        StackUpUpConfig.general.maxStackSize = 10240
        StackUpUpConfig.activeMaxStackSize = 10240
    }

    @AfterEach
    fun restoreMaxStackSize() {
        StackUpUpConfig.general.maxStackSize = previousMaxStackSize
        StackUpUpConfig.activeMaxStackSize = previousMaxStackSize
    }

    @Test
    fun `shouldAggregateMultipleFilesInOrder`() {
        val tempDir = createTempDirectory("stackupup-multi-source").toFile()
        val pack = File(tempDir, "pack.su").apply {
            writeText("item = minecraft:egg -> 64" + System.lineSeparator(), Charsets.UTF_8)
        }
        val world = File(tempDir, "world.su").apply {
            writeText("item = minecraft:egg -> 128" + System.lineSeparator(), Charsets.UTF_8)
        }

        val result = DslRuleSource.fromFiles(listOf(pack, world))
        val service = StackLimitService(result.snapshot)
        val context = StackContext("minecraft:egg", "minecraft", 0, "item", 16, emptySet())

        assertEquals(128, service.resolve(context))
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `shouldFilterConditionalBlocksAcrossMultipleFiles`() {
        val tempDir = createTempDirectory("stackupup-multi-source").toFile()
        val pack = File(tempDir, "pack.su").apply {
            writeText(
                """
                if mod = ftbquests
                  item = minecraft:egg -> 64
                end
                """.trimIndent() + System.lineSeparator(),
                Charsets.UTF_8,
            )
        }
        val world = File(tempDir, "world.su").apply {
            writeText(
                """
                if mod = gamestages
                  item = minecraft:egg -> 128
                end
                """.trimIndent() + System.lineSeparator(),
                Charsets.UTF_8,
            )
        }

        val result = DslRuleSource.fromFiles(
            files = listOf(pack, world),
            gateContext = RuleGateContext(loadedMods = setOf("gamestages")),
        )
        val service = StackLimitService(result.snapshot)
        val context = StackContext("minecraft:egg", "minecraft", 0, "item", 16, emptySet())

        assertEquals(128, service.resolve(context))
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `errors_shouldIncludeSourceFileName`() {
        val tempDir = createTempDirectory("stackupup-multi-source").toFile()
        val broken = File(tempDir, "broken.su").apply {
            writeText("item = minecraft:egg ??? 64" + System.lineSeparator(), Charsets.UTF_8)
        }

        val result = DslRuleSource.fromFiles(listOf(broken))

        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().format().contains("broken.su"))
    }
}
