package io.alexjoest.stackupup.rules.io

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.limit.StackLimitService

class DslRuleSourceMultiFileTest {
    @Test
    fun `应当按给定顺序聚合多个规则文件`() {
        val tempDir = createTempDirectory("stackupup-multi-source").toFile()
        val pack = File(tempDir, "pack.su").apply {
            writeText("item = minecraft:egg -> 64" + System.lineSeparator(), Charsets.UTF_8)
        }
        val world = File(tempDir, "world.su").apply {
            writeText("item = minecraft:egg -> 128" + System.lineSeparator(), Charsets.UTF_8)
        }

        val result = DslRuleSource.fromFiles(listOf(pack, world)).load()
        val service = StackLimitService(result.snapshot)
        val context = StackContext("minecraft:egg", "minecraft", 0, "item", 16, emptySet())

        assertEquals(128, service.resolve(context))
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `应当在错误中包含来源文件名`() {
        val tempDir = createTempDirectory("stackupup-multi-source").toFile()
        val broken = File(tempDir, "broken.su").apply {
            writeText("item = minecraft:egg ??? 64" + System.lineSeparator(), Charsets.UTF_8)
        }

        val result = DslRuleSource.fromFiles(listOf(broken)).load()

        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().contains("broken.su"))
    }
}
