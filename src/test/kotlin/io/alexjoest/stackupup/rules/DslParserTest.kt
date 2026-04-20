package io.alexjoest.stackupup.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.rules.parse.DslParser

class DslParserTest {
    @Test
    fun `应当解析链式比较`() {
        val rule = DslParser.parseLine("2 < size < 64 -> 1024")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(1024), rule.action.steps.map { it.value })
        assertEquals(listOf("size"), rule.condition.debugFields())
    }

    @Test
    fun `应当解析 in 列表`() {
        val rule = DslParser.parseLine("item in [minecraft:egg, minecraft:snowball] -> 128")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(128), rule.action.steps.map { it.value })
        assertEquals(2, rule.condition.debugLiteralCount())
    }

    @Test
    fun `应当在列表条件后继续解析与条件`() {
        val rule = DslParser.parseLine("item in [minecraft:egg, minecraft:snowball] && metadata = 0 -> 128")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(128), rule.action.steps.map { it.value })
        assertEquals(listOf("item", "meta"), rule.condition.debugFields())
        assertEquals(3, rule.condition.debugLiteralCount())
    }

    @Test
    fun `应当解析乘法动作运算符`() {
        val rule = DslParser.parseLine("size > 2 -> *4")
        assertEquals(listOf("multiply"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(4), rule.action.steps.map { it.value })
        assertEquals(listOf("size"), rule.condition.debugFields())
    }

    @Test
    fun `应当把符号别名统一归一到 token 流`() {
        val rule = DslParser.parseLine("item = gregtech:gt.metaitem.01 && metadata in [1, 2, 3] -> 1024")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(1024), rule.action.steps.map { it.value })
        assertEquals(listOf("item", "meta"), rule.condition.debugFields())
        assertEquals(4, rule.condition.debugLiteralCount())
    }

    @Test
    fun `应当解析流式动作链`() {
        val rule = DslParser.parseLine("size > 1 -> *2 -> +10 -> /2")
        assertEquals(listOf("multiply", "add", "divide"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(2, 10, 2), rule.action.steps.map { it.value })
    }
}

