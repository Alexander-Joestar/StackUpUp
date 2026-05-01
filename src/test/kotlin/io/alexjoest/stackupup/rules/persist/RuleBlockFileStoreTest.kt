package io.alexjoest.stackupup.rules.persist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RuleBlockFileStoreTest {
    @Test
    fun `shouldInsertAndReplaceNamedBlock`() {
        val tempDir = createTempDirectory("stackupup-rule-block-store").toFile()
        val file = File(tempDir, "world.su")
        val store = RuleBlockFileStore(file)

        store.replaceBlock(
            RuleTextBlock(
                id = "ftbquests.reward_sizes",
                lines = listOf("item = minecraft:egg -> 64"),
            ),
        )
        store.replaceBlock(
            RuleTextBlock(
                id = "ftbquests.reward_sizes",
                lines = listOf("item = minecraft:snowball -> 16"),
            ),
        )

        val blocks = store.readBlocks()
        assertEquals(1, blocks.size)
        assertEquals("ftbquests.reward_sizes", blocks.single().id)
        assertEquals(listOf("item = minecraft:snowball -> 16"), blocks.single().lines)
    }

    @Test
    fun `shouldPreserveTextOutsideBlocks`() {
        val tempDir = createTempDirectory("stackupup-rule-block-store").toFile()
        val file = File(tempDir, "world.su")
        file.writeText(
            """
            # header
            # BEGIN stackupup:block old.block
            item = minecraft:egg -> 64
            # END stackupup:block old.block
            """.trimIndent() + System.lineSeparator(),
            Charsets.UTF_8,
        )
        val store = RuleBlockFileStore(file)

        store.replaceBlock(
            RuleTextBlock(
                id = "new.block",
                lines = listOf("item = minecraft:snowball -> 16"),
            ),
        )

        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("# header"))
        assertTrue(text.contains("# BEGIN stackupup:block old.block"))
        assertTrue(text.contains("# BEGIN stackupup:block new.block"))
    }
}
