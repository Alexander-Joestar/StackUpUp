package pl.asie.stackup.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.asie.stackup.rules.compile.RuleCompiler
import pl.asie.stackup.rules.model.RuleMatchContext

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
    fun `应当按顺序保留乘法动作`() {
        val compiled = RuleCompiler.compileLine("ore = ingotSteel *= 2", 9)
        assertEquals("*=", compiled.action.operator)
        assertEquals(2, compiled.action.value)
    }

    @Test
    fun `应当支持 size 区间匹配`() {
        val compiled = RuleCompiler.compileLine("size > 2 && size < 64 -> 1024", 10)
        assertEquals(true, compiled.matches(RuleMatchContext("minecraft:egg", "minecraft", 0, 16, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:stick", "minecraft", 0, 64, "item", emptySet())))
    }
}
