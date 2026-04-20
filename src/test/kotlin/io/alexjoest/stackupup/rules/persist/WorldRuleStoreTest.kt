package io.alexjoest.stackupup.rules.persist

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorldRuleStoreTest {
    @Test
    fun `应当按来源块写入并保留原始规则顺序`() {
        val tempDir = createTempDirectory("stackupup-world-rule-store").toFile()
        val store = WorldRuleStore(File(tempDir, "world.su"))

        store.replaceSourceBlock(
            sourceId = "ftbquests.chapter_1",
            lines = listOf(
                "item = minecraft:egg -> 1",
                "item = minecraft:snowball -> 1"
            )
        )

        assertEquals(
            listOf(
                "item = minecraft:egg -> 1",
                "item = minecraft:snowball -> 1"
            ),
            store.readBlocks().single().lines
        )
    }

    @Test
    fun `再次写入同一来源时应整块替换`() {
        val tempDir = createTempDirectory("stackupup-world-rule-store").toFile()
        val store = WorldRuleStore(File(tempDir, "world.su"))

        store.replaceSourceBlock(
            sourceId = "ftbquests.chapter_1",
            lines = listOf("item = minecraft:egg -> 1")
        )
        store.replaceSourceBlock(
            sourceId = "ftbquests.chapter_1",
            lines = listOf(
                "item = minecraft:egg -> 2",
                "size > 64 -> *2"
            )
        )

        assertEquals(
            listOf(
                "item = minecraft:egg -> 2",
                "size > 64 -> *2"
            ),
            store.readBlocks().single().lines
        )
    }
}
