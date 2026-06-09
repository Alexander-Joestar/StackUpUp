package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.model.RuleMatchContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuleCompilerTest {
    @Test
    fun `shouldCompileItemInListToMatchAny`() {
        val compiled = RuleCompiler.compileLine("item in [minecraft:egg, minecraft:snowball] -> 128", 7)
        val egg = RuleMatchContext(
            itemId = "minecraft:egg",
            modId = "minecraft",
            meta = 0,
            baseSize = 16,
            type = "item",
            oreNames = emptySet(),
        )
        val snowball = RuleMatchContext(
            itemId = "minecraft:snowball",
            modId = "minecraft",
            meta = 0,
            baseSize = 16,
            type = "item",
            oreNames = emptySet(),
        )
        assertEquals(true, compiled.matches(egg))
        assertEquals(true, compiled.matches(snowball))
    }

    @Test
    fun `shouldCompileOrCondition`() {
        val compiled = RuleCompiler.compileLine("mod = thermal || mod = ic2 -> 512", 8)
        val thermal = RuleMatchContext("thermal:foo", "thermal", 0, 16, "item", emptySet())
        val ic2 = RuleMatchContext("ic2:bar", "ic2", 0, 16, "item", emptySet())
        val vanilla = RuleMatchContext("minecraft:egg", "minecraft", 0, 16, "item", emptySet())
        assertEquals(true, compiled.matches(thermal))
        assertEquals(true, compiled.matches(ic2))
        assertEquals(false, compiled.matches(vanilla))
    }

    @Test
    fun `shouldSupportModListWildcard`() {
        val compiled = RuleCompiler.compileLine("mod in [therm*, ic2] -> 512", 8)
        val thermal = RuleMatchContext("thermal:foo", "thermalexpansion", 0, 16, "item", emptySet())
        val ic2 = RuleMatchContext("ic2:bar", "ic2", 0, 16, "item", emptySet())
        val vanilla = RuleMatchContext("minecraft:egg", "minecraft", 0, 16, "item", emptySet())

        assertEquals(true, compiled.matches(thermal))
        assertEquals(true, compiled.matches(ic2))
        assertEquals(false, compiled.matches(vanilla))
    }

    @Test
    fun `shouldPreserveMultiplyOrder`() {
        val compiled = RuleCompiler.compileLine("ore = ingotSteel -> *2", 9)
        assertEquals(1, compiled.action.steps.size)
        assertEquals("multiply", compiled.action.steps.single().debugName)
        assertEquals(2, compiled.action.steps.single().value)
    }

    @Test
    fun `shouldSupportActionChain`() {
        val compiled = RuleCompiler.compileLine("ore = ingotSteel -> *2 -> +10", 10)
        assertEquals(listOf("multiply", "add"), compiled.action.steps.map { it.debugName })
        assertEquals(listOf(2, 10), compiled.action.steps.map { it.value })
    }

    @Test
    fun `shouldSupportSizeRange`() {
        val compiled = RuleCompiler.compileLine("size > 2 && size < 64 -> 1024", 11)
        assertEquals(true, compiled.matches(RuleMatchContext("minecraft:egg", "minecraft", 0, 16, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:stick", "minecraft", 0, 64, "item", emptySet())))
    }

    @Test
    fun `shouldSupportCompactSizeComparison`() {
        val compiled = RuleCompiler.compileLine("size >2 -> 1000000", 11)
        assertEquals(true, compiled.matches(RuleMatchContext("minecraft:egg", "minecraft", 0, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:sword", "minecraft", 0, 1, "item", emptySet())))
    }

    @Test
    fun `shouldSupportItemWithMetadataSugar`() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01:11305 -> 1024", 12)
        val matched = RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 11305, 64, "item", emptySet())
        val otherMeta = RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 42, 64, "item", emptySet())

        assertEquals(true, compiled.matches(matched))
        assertEquals(false, compiled.matches(otherMeta))
    }

    @Test
    fun `shouldSupportOreWildcardMatch`() {
        val compiled = RuleCompiler.compileLine("ore != ingot* -> 64", 12)
        val ingot = RuleMatchContext("minecraft:iron_ingot", "minecraft", 0, 64, "item", setOf("ingotIron"))
        val dust = RuleMatchContext("minecraft:gunpowder", "minecraft", 0, 64, "item", setOf("dustSulfur"))

        assertEquals(false, compiled.matches(ingot))
        assertEquals(true, compiled.matches(dust))
    }

    @Test
    fun `shouldSupportItemAtMetadataSugar`() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01@11305 -> 1024", 13)
        val matched = RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 11305, 64, "item", emptySet())
        val otherMeta = RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 42, 64, "item", emptySet())

        assertEquals(true, compiled.matches(matched))
        assertEquals(false, compiled.matches(otherMeta))
    }

    @Test
    fun `shouldSupportMetaAsAlias`() {
        val compiled = RuleCompiler.compileLine("metadata in [1, 2, 3] -> 512", 14)
        assertEquals(true, compiled.matches(RuleMatchContext("minecraft:egg", "minecraft", 2, 16, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:egg", "minecraft", 4, 16, "item", emptySet())))
    }

    @Test
    fun `itemWithoutMeta_shouldMatchWholeDomain`() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01 -> 1024", 15)

        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 1, 64, "item", emptySet())))
        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 11305, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:meta_ingot", "gregtech", 324, 64, "item", emptySet())))
    }

    @Test
    fun `shouldSupportItemListWithMeta`() {
        val compiled = RuleCompiler.compileLine(
            "item in [gregtech:gt.metaitem.01:1, gregtech:gt.metaitem.01:2] && mod = gregtech -> 1024",
            16,
        )

        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 2, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 3, 64, "item", emptySet())))
    }

    @Test
    fun `shouldSupportItemAndMetaList`() {
        val compiled = RuleCompiler.compileLine(
            "item = gregtech:gt.metaitem.01 && meta in [1, 2, 3] -> 1024",
            17,
        )

        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 2, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 4, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:meta_ingot", "gregtech", 2, 64, "item", emptySet())))
    }

    @Test
    fun `and_shouldHaveHigherPrecedenceThanOr`() {
        val compiled = RuleCompiler.compileLine(
            "mod = thermal || item = gregtech:gt.metaitem.01 && metadata = 11305 -> 256",
            18,
        )

        assertEquals(true, compiled.matches(RuleMatchContext("thermal:foo", "thermal", 0, 64, "item", emptySet())))
        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 11305, 64, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:gt.metaitem.01", "gregtech", 1, 64, "item", emptySet())))
    }

    @Test
    fun `itemWildcard_shouldMatchStackableOnly`() {
        val compiled = RuleCompiler.compileLine("item = * -> 128", 19)

        // baseSize > 1 可堆叠
        assertEquals(true, compiled.matches(RuleMatchContext("minecraft:egg", "minecraft", 0, 16, "item", emptySet())))
        assertEquals(true, compiled.matches(RuleMatchContext("minecraft:stick", "minecraft", 0, 64, "item", emptySet())))

        // baseSize = 1 不可堆叠（工具、装备、桶等）
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:diamond_sword", "minecraft", 0, 1, "item", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:water_bucket", "minecraft", 0, 1, "item", emptySet())))
    }

    @Test
    fun `shouldMatchTabField`() {
        val compiled = RuleCompiler.compileLine("tab = buildingBlocks -> 256", 20)

        assertEquals(true, compiled.matches(RuleMatchContext("minecraft:stone", "minecraft", 0, 64, "block", emptySet(), tab = "buildingBlocks")))
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:stick", "minecraft", 0, 64, "item", emptySet(), tab = "tools")))
    }

    @Test
    fun `shouldMatchMaterialFieldExactly`() {
        val compiled = RuleCompiler.compileLine("material = steel -> 2048", 21)

        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:meta_item_1", "gregtech", 1000, 64, "item", emptySet(), material = "steel")))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:meta_item_1", "gregtech", 1001, 64, "item", emptySet(), material = "copper")))
    }

    @Test
    fun `shouldMatchMaterialListAndRejectEmptyDefault`() {
        val compiled = RuleCompiler.compileLine("material in [steel, copper] -> 2048", 22)

        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:meta_item_1", "gregtech", 1000, 64, "item", emptySet(), material = "steel")))
        assertEquals(true, compiled.matches(RuleMatchContext("gregtech:meta_item_1", "gregtech", 1001, 64, "item", emptySet(), material = "copper")))
        assertEquals(false, compiled.matches(RuleMatchContext("gregtech:meta_item_1", "gregtech", 1002, 64, "item", emptySet())))
    }

    @Test
    fun `shouldMatchMetaRange`() {
        val compiled = RuleCompiler.compileLine("100 < meta < 300 -> 512", 23)

        assertEquals(true, compiled.matches(RuleMatchContext("minecraft:wool", "minecraft", 150, 64, "block", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:wool", "minecraft", 50, 64, "block", emptySet())))
        assertEquals(false, compiled.matches(RuleMatchContext("minecraft:wool", "minecraft", 400, 64, "block", emptySet())))
    }
}
