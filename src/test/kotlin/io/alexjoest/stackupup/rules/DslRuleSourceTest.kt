package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.rules.io.DslRuleSource
import io.alexjoest.stackupup.rules.io.RuleGateContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DslRuleSourceTest {
    @Test
    fun `shouldSkipCommentsAndEmptyLines`() {
        val result = DslRuleSource.fromLines(
            listOf(
                "# 注释",
                "// 注释",
                "",
                "item = minecraft:egg -> 64",
            ),
        )

        assertEquals(1, result.snapshot.rules.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `shouldSkipBlockAndTrailingComments`() {
        val result = DslRuleSource.fromLines(
            listOf(
                "/* 整行块注释 */",
                "item = minecraft:egg -> 64 // 行尾注释",
                "/* 多行",
                "块注释 */",
                "item = minecraft:snowball -> 16 # 井号注释",
            ),
        )

        assertEquals(2, result.snapshot.rules.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `shouldCollectErrorsAndKeepValidRules`() {
        val result = DslRuleSource.fromLines(
            listOf(
                "item = minecraft:egg -> 64",
                "item = minecraft:stick ??? 32",
            ),
        )

        assertEquals(1, result.snapshot.rules.size)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `ifBlock_shouldLoadRulesWhenGateMatches`() {
        val result = DslRuleSource.fromLines(
            lines = listOf(
                "if mod = gamestages",
                "  item = minecraft:egg -> 128",
                "end",
            ),
            gateContext = RuleGateContext(
                loadedMods = setOf("gamestages"),
            ),
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.snapshot.rules.size)
    }

    @Test
    fun `ifBlock_shouldSkipRulesWhenGateDoesNotMatch`() {
        val result = DslRuleSource.fromLines(
            lines = listOf(
                "if mod = ftbquests",
                "  item = minecraft:egg -> 128",
                "end",
                "item = minecraft:stick -> 32",
            ),
            gateContext = RuleGateContext(
                loadedMods = setOf("gamestages"),
            ),
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.snapshot.rules.size)
    }

    @Test
    fun `ifBlock_shouldSupportNestedParentGateSkip`() {
        val result = DslRuleSource.fromLines(
            lines = listOf(
                "if mod = missing",
                "  if mod = gamestages",
                "    item = minecraft:egg -> 128",
                "  end",
                "end",
            ),
            gateContext = RuleGateContext(
                loadedMods = setOf("gamestages"),
            ),
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(0, result.snapshot.rules.size)
    }

    @Test
    fun `missingFile_shouldAutoCreateTemplate`() {
        val tempDir = createTempDirectory("stackupup-rule-source").toFile()
        val file = File(tempDir, "main.su")

        val result = DslRuleSource.fromFile(file)

        assertTrue(file.exists())
        assertEquals("", file.readText(Charsets.UTF_8))
        assertEquals(0, result.snapshot.rules.size)
    }
}
