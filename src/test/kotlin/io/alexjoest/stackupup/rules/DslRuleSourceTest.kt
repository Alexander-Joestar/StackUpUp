package io.alexjoest.stackupup.rules

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.rules.io.DslRuleSource

class DslRuleSourceTest {
    @Test
    fun `应当忽略单行注释与空行`() {
        val result = DslRuleSource.fromLines(
            listOf(
                "# 注释",
                "// 注释",
                "",
                "item = minecraft:egg -> 64"
            )
        )

        assertEquals(1, result.snapshot.rules.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `应当忽略块注释与行尾注释`() {
        val result = DslRuleSource.fromLines(
            listOf(
                "/* 整行块注释 */",
                "item = minecraft:egg -> 64 // 行尾注释",
                "/* 多行",
                "块注释 */",
                "item = minecraft:snowball -> 16 # 井号注释"
            )
        )

        assertEquals(2, result.snapshot.rules.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `应当收集错误并保留有效规则`() {
        val result = DslRuleSource.fromLines(
            listOf(
                "item = minecraft:egg -> 64",
                "item = minecraft:stick ??? 32"
            )
        )

        assertEquals(1, result.snapshot.rules.size)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `规则文件不存在时应自动创建示例文件`() {
        val tempDir = createTempDirectory("stackupup-rule-source").toFile()
        val file = File(tempDir, "main.su")

        val result = DslRuleSource.fromFile(file)

        assertTrue(file.exists())
        assertFalse(file.readText(Charsets.UTF_8).isBlank())
        assertEquals(0, result.snapshot.rules.size)
    }
}

