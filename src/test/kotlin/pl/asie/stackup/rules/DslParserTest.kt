package pl.asie.stackup.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.asie.stackup.rules.parse.DslParser

class DslParserTest {
    @Test
    fun `应当解析链式比较`() {
        val rule = DslParser.parseLine("2 < size < 64 -> 1024")
        assertEquals(1024, rule.action.value)
        assertEquals(listOf("size"), rule.condition.debugFields())
    }

    @Test
    fun `应当解析 in 列表`() {
        val rule = DslParser.parseLine("item in [minecraft:egg, minecraft:snowball] -> 128")
        assertEquals(128, rule.action.value)
        assertEquals(2, rule.condition.debugLiteralCount())
    }
}
