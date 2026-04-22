package io.alexjoest.stackupup.rules.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleLineLoaderTest {
    @Test
    fun `应忽略空行与行注释`() {
        val result = RuleLineLoader.load(
            listOf(
                RuleLineLoader.RuleLineInput("# comment", 1, "main.su"),
                RuleLineLoader.RuleLineInput("   ", 2, "main.su"),
                RuleLineLoader.RuleLineInput("item = minecraft:egg -> 64 // tail", 3, "main.su")
            )
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.snapshot.rules.size)
    }

    @Test
    fun `应跨行处理块注释`() {
        val result = RuleLineLoader.load(
            listOf(
                RuleLineLoader.RuleLineInput("/* begin", 1, "main.su"),
                RuleLineLoader.RuleLineInput("still comment", 2, "main.su"),
                RuleLineLoader.RuleLineInput("end */ item = minecraft:egg -> 64", 3, "main.su")
            )
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.snapshot.rules.size)
    }

    @Test
    fun `错误消息应包含来源文件与真实行号`() {
        val result = RuleLineLoader.load(
            listOf(
                RuleLineLoader.RuleLineInput("item = minecraft:egg -> /", 7, "pack.su")
            )
        )

        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().contains("[pack.su]"))
        assertTrue(result.errors.single().contains("第 7 行"))
    }
}
