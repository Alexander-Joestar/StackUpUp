package pl.asie.stackup.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.asie.stackup.rules.io.DslRuleSource

class DslRuleSourceTest {
    @Test
    fun `应当忽略注释与空行`() {
        val source = DslRuleSource.fromLines(
            listOf(
                "# 注释",
                "",
                "item = minecraft:egg -> 64"
            )
        )

        val result = source.load()
        assertEquals(1, result.snapshot.rules.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `应当收集错误并保留有效规则`() {
        val source = DslRuleSource.fromLines(
            listOf(
                "item = minecraft:egg -> 64",
                "item = minecraft:stick ??? 32"
            )
        )

        val result = source.load()
        assertEquals(1, result.snapshot.rules.size)
        assertEquals(1, result.errors.size)
    }
}
