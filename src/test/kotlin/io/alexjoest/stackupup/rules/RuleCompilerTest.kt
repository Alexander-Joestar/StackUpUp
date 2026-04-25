package io.alexjoest.stackupup.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.model.RuleMatchContext

class RuleCompilerTest {
    @Test
    fun `应当把 item in 列表编译成可匹配任一项的规则`() {
        val compiled = RuleCompiler.compileLine("item in [minecraft:egg, minecraft:snowball] -> 128", 7)
        val egg = RuleMatchContext(
            itemId = "minecraft:egg",
            modId = "minecraft",
            meta = 0,
            baseSize = 16,
            type = "item",
            oreNames = emptySet()
        )
        val snowball = RuleMatchContext(
            itemId = "minecraft:snowball",
            modId = "minecraft",
            meta = 0,
            baseSize = 16,
            type = "item",
            oreNames = emptySet()
        )
        assertEquals(true, compiled.matches(egg))
        assertEquals(true, compiled.matches(snowball))
    }

    @Test
    fun `应当支持或条件编译`() {
        val compiled = RuleCompiler.compileLine("mod = thermal || mod = ic2 -> 512", 8)
        val thermal = RuleMatchContext("thermal:foo", "thermal", 0, 16, "item", emptySet())
        val ic2 = RuleMatchContext("ic2:bar", "ic2", 0, 16, "item", emptySet())
        val vanilla = RuleMatchContext("minecraft:egg", "minecraft", 0, 16, "item", emptySet())
        assertEquals(true, compiled.matches(thermal))
        assertEquals(true, compiled.matches(ic2))
        assertEquals(false, compiled.matches(vanilla))
    }

    @Test
    fun `应当支持 mod 列表中的通配匹配`() {
        val compiled = RuleCompiler.compileLine("mod in [therm*, ic2] -> 512", 8)
        val thermal = RuleMatchContext("thermal:foo", "thermalexpansion", 0, 16, "item", emptySet())
        val ic2 = RuleMatchContext("ic2:bar", "ic2", 0, 16, "item", emptySet())
        val vanilla = RuleMatchContext("minecraft:egg", "minecraft", 0, 16, "item", emptySet())

        assertEquals(true, compiled.matches(thermal))
        assertEquals(true, compiled.matches(ic2))
        assertEquals(false, compiled.matches(vanilla))
    }

    @Test
    fun `应当按顺序保留乘法动作`() {
        val compiled = RuleCompiler.compileLine("ore = ingotSteel -> *2", 9)
        assertEquals(1, compiled.action.steps.size)
        assertEquals("multiply", compiled.action.steps.single().debugName)
        assertEquals(2, compiled.action.steps.single().value)
    }

    @Test
    fun `应当支持流式动作链`() {
        val compiled = RuleCompiler.compileLine("ore = ingotSteel -> *2 -> +10", 10)
        assertEquals(listOf("multiply", "add"), compiled.action.steps.map { it.debugName })
        assertEquals(listOf(2, 10), compiled.action.steps.map { it.value })
    }

    @Test
    fun `应当支持 size 区间匹配`() {
        val compiled = RuleCompiler.compileLine("size > 2 && size < 64 -> 1024", 11)
        assertEquals(true, compiled.matches(RuleMatchContext("minecraft:egg", "minecraft", 0, 16, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:stick", "minecraft", 0, 64, "item", emptySet())))
    }

    @Test
    fun `应当兼容不带空格的 size 比较写法`() {
        val compiled = RuleCompiler.compileLine("size >2 -> 1000000", 11)
        assertEquals(true, compiled.matches(RuleMatchContext("minecraft:egg", "minecraft", 0, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:sword", "minecraft", 0, 1, "item", emptySet())))
    }

    @Test
    fun `应当支持 item 带 metadata 的语法糖`() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01:11305 -> 1024", 12)
        val matched = RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 11305, 64, "item", emptySet())
        val otherMeta = RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 42, 64, "item", emptySet())

        assertEquals(true, compiled.matches(matched))
        assertEquals(false, compiled.matches(otherMeta))
    }

    @Test
    fun `应当支持 ore 的非等值通配匹配`() {
        val compiled = RuleCompiler.compileLine("ore != ingot* -> 64", 12)
        val ingot = RuleMatchContext("minecraft:iron_ingot", "minecraft", 0, 64, "item", setOf("ingotIron"))
        val dust = RuleMatchContext("minecraft:gunpowder", "minecraft", 0, 64, "item", setOf("dustSulfur"))

        assertEquals(false, compiled.matches(ingot))
        assertEquals(true, compiled.matches(dust))
    }

    @Test
    fun `应当兼容 item at metadata 语法糖`() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01@11305 -> 1024", 13)
        val matched = RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 11305, 64, "item", emptySet())
        val otherMeta = RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 42, 64, "item", emptySet())

        assertEquals(true, compiled.matches(matched))
        assertEquals(false, compiled.matches(otherMeta))
    }

    @Test
    fun `应当支持 metadata 作为 meta 的别名`() {
        val compiled = RuleCompiler.compileLine("metadata in [1, 2, 3] -> 512", 14)
        assertEquals(true, compiled.matches(RuleMatchContext("minecraft:egg", "minecraft", 2, 16, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:egg", "minecraft", 4, 16, "item", emptySet())))
    }

    @Test
    fun `应当让不带 meta 的 item 条件匹配整个物品域`() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01 -> 1024", 15)

        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 1, 64, "item", emptySet())))
        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 11305, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:meta_ingot", "gregtech", 324, 64, "item", emptySet())))
    }

    @Test
    fun `应当支持 item 列表与 metadata 条件组合`() {
        val compiled = RuleCompiler.compileLine(
            "item in [gregtech:gt.metaitem.01:1, gregtech:gt.metaitem.01:2] && mod = gregtech -> 1024",
            16
        )

        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 2, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 3, 64, "item", emptySet())))
    }

    @Test
    fun `应当支持 item 与 meta 列表组合`() {
        val compiled = RuleCompiler.compileLine(
            "item = gregtech:gt.metaitem.01 && meta in [1, 2, 3] -> 1024",
            17
        )

        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 2, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 4, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:meta_ingot", "gregtech", 2, 64, "item", emptySet())))
    }

    @Test
    fun `应当保持与高于或的优先级`() {
        val compiled = RuleCompiler.compileLine(
            "mod = thermal || item = gregtech:gt.metaitem.01 && metadata = 11305 -> 256",
            18
        )

        assertEquals(true, compiled.matches(RuleMatchContext("thermal:foo", "thermal", 0, 64, "item", emptySet())))
        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 11305, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 1, 64, "item", emptySet())))
    }
}

