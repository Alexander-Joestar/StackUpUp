package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.rules.parse.DslParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DslParserTest {
    @Test
    fun `shouldParseChainedComparison`() {
        val rule = DslParser.parseLine("2 < size < 64 -> 1024")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(1024), rule.action.steps.map { it.value })
        assertEquals(listOf(RuleField.SIZE), rule.condition.debugFields())
    }

    @Test
    fun `shouldParseInList`() {
        val rule = DslParser.parseLine("item in [minecraft:egg, minecraft:snowball] -> 128")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(128), rule.action.steps.map { it.value })
        assertEquals(2, rule.condition.debugLiteralCount())
    }

    @Test
    fun `shouldParseAndAfterListCondition`() {
        val rule = DslParser.parseLine("item in [minecraft:egg, minecraft:snowball] && metadata = 0 -> 128")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(128), rule.action.steps.map { it.value })
        assertEquals(listOf(RuleField.ITEM, RuleField.META), rule.condition.debugFields())
        assertEquals(3, rule.condition.debugLiteralCount())
    }

    @Test
    fun `shouldParseMultiplyOperator`() {
        val rule = DslParser.parseLine("size > 2 -> *4")
        assertEquals(listOf("multiply"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(4), rule.action.steps.map { it.value })
        assertEquals(listOf(RuleField.SIZE), rule.condition.debugFields())
    }

    @Test
    fun `shouldNormalizeSymbolAliases`() {
        val rule = DslParser.parseLine("item = gregtech:gt.metaitem.01 && metadata in [1, 2, 3] -> 1024")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(1024), rule.action.steps.map { it.value })
        assertEquals(listOf(RuleField.ITEM, RuleField.META), rule.condition.debugFields())
        assertEquals(4, rule.condition.debugLiteralCount())
    }

    @Test
    fun `shouldParseActionChain`() {
        val rule = DslParser.parseLine("size > 1 -> *2 -> +10 -> /2")
        assertEquals(listOf("multiply", "add", "divide"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(2, 10, 2), rule.action.steps.map { it.value })
    }
}
