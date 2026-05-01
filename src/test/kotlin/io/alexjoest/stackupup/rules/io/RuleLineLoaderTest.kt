package io.alexjoest.stackupup.rules.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleLineLoaderTest {
    @Test
    fun `shouldSkipEmptyAndCommentLines`() {
        val result = RuleLineLoader.load(
            listOf(
                RuleLineLoader.RuleLineInput("# comment", 1, "main.su"),
                RuleLineLoader.RuleLineInput("   ", 2, "main.su"),
                RuleLineLoader.RuleLineInput("item = minecraft:egg -> 64 // tail", 3, "main.su"),
            ),
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.snapshot.rules.size)
    }

    @Test
    fun `shouldHandleMultilineBlockComments`() {
        val result = RuleLineLoader.load(
            listOf(
                RuleLineLoader.RuleLineInput("/* begin", 1, "main.su"),
                RuleLineLoader.RuleLineInput("still comment", 2, "main.su"),
                RuleLineLoader.RuleLineInput("end */ item = minecraft:egg -> 64", 3, "main.su"),
            ),
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.snapshot.rules.size)
    }

    @Test
    fun `errors_shouldIncludeSourceFileAndLine`() {
        val result = RuleLineLoader.load(
            listOf(
                RuleLineLoader.RuleLineInput("item = minecraft:egg -> /", 7, "pack.su"),
            ),
        )

        assertEquals(1, result.errors.size)
        val formatted = result.errors.single().format()
        assertTrue(formatted.contains("[pack.su]"))
        assertTrue(formatted.contains("7"))
    }

    @Test
    fun `shouldStopCompilingAfterFirstParseError`() {
        val result = RuleLineLoader.load(
            listOf(
                RuleLineLoader.RuleLineInput("item = minecraft:egg -> 64", 1, "main.su"),
                RuleLineLoader.RuleLineInput("item = minecraft:egg -> /", 2, "main.su"),
                RuleLineLoader.RuleLineInput("item = minecraft:egg -> 128", 3, "main.su"),
            ),
        )

        assertEquals(1, result.snapshot.rules.size)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `shouldCompileValidRulesBeforeParseError`() {
        val result = RuleLineLoader.load(
            listOf(
                RuleLineLoader.RuleLineInput("item = minecraft:egg -> 64", 1, "main.su"),
                RuleLineLoader.RuleLineInput("item = minecraft:egg -> /", 2, "main.su"),
            ),
        )

        assertEquals(1, result.snapshot.rules.size)
        assertEquals(1, result.errors.size)
    }
}
