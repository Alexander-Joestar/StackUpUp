package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuleCompilerTest {
    @Test
    fun `shouldCompileItemInListToMatchAny`() {
        val compiled = RuleCompiler.compileLine("item in [minecraft:egg, minecraft:snowball] -> 128", 7)
        val egg = ctx("minecraft:egg", baseSize = 16)
        val snowball = ctx("minecraft:snowball", baseSize = 16)
        assertEquals(true, compiled.matches(egg))
        assertEquals(true, compiled.matches(snowball))
    }

    @Test
    fun `shouldCompileOrCondition`() {
        val compiled = RuleCompiler.compileLine("mod = thermal || mod = ic2 -> 512", 8)
        val thermal = ctx("thermal:foo", baseSize = 16)
        val ic2 = ctx("ic2:bar", baseSize = 16)
        val vanilla = ctx("minecraft:egg", baseSize = 16)
        assertEquals(true, compiled.matches(thermal))
        assertEquals(true, compiled.matches(ic2))
        assertEquals(false, compiled.matches(vanilla))
    }

    @Test
    fun `shouldSupportModListWildcard`() {
        val compiled = RuleCompiler.compileLine("mod in [therm*, ic2] -> 512", 8)
        val thermal = ctx("thermal:foo", modId = "thermalexpansion", baseSize = 16)
        val ic2 = ctx("ic2:bar", baseSize = 16)
        val vanilla = ctx("minecraft:egg", baseSize = 16)

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
        assertEquals(true, compiled.matches(ctx("minecraft:egg", baseSize = 16)))
        assertEquals(false, compiled.matches(ctx("minecraft:stick")))
    }

    @Test
    fun `shouldSupportCompactSizeComparison`() {
        val compiled = RuleCompiler.compileLine("size >2 -> 1000000", 11)
        assertEquals(true, compiled.matches(ctx("minecraft:egg")))
        assertEquals(false, compiled.matches(ctx("minecraft:sword", baseSize = 1)))
    }

    @Test
    fun `shouldSupportItemWithMetadataSugar`() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01:11305 -> 1024", 12)
        val matched = ctx("gregtech:gt.metaitem.01", meta = 11305)
        val otherMeta = ctx("gregtech:gt.metaitem.01", meta = 42)

        assertEquals(true, compiled.matches(matched))
        assertEquals(false, compiled.matches(otherMeta))
    }

    @Test
    fun `shouldSupportOreWildcardMatch`() {
        val compiled = RuleCompiler.compileLine("ore != ingot* -> 64", 12)
        val ingot = ctx("minecraft:iron_ingot", oreNames = setOf("ingotIron"))
        val dust = ctx("minecraft:gunpowder", oreNames = setOf("dustSulfur"))

        assertEquals(false, compiled.matches(ingot))
        assertEquals(true, compiled.matches(dust))
    }

    @Test
    fun `shouldSupportItemAtMetadataSugar`() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01@11305 -> 1024", 13)
        val matched = ctx("gregtech:gt.metaitem.01", meta = 11305)
        val otherMeta = ctx("gregtech:gt.metaitem.01", meta = 42)

        assertEquals(true, compiled.matches(matched))
        assertEquals(false, compiled.matches(otherMeta))
    }

    @Test
    fun `shouldSupportMetaAsAlias`() {
        val compiled = RuleCompiler.compileLine("metadata in [1, 2, 3] -> 512", 14)
        assertEquals(true, compiled.matches(ctx("minecraft:egg", meta = 2, baseSize = 16)))
        assertEquals(false, compiled.matches(ctx("minecraft:egg", meta = 4, baseSize = 16)))
    }

    @Test
    fun `itemWithoutMeta_shouldMatchWholeDomain`() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01 -> 1024", 15)

        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 1)))
        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 11305)))
        assertEquals(false, compiled.matches(ctx("gregtech:meta_ingot", meta = 324)))
    }

    @Test
    fun `shouldSupportItemListWithMeta`() {
        val compiled = RuleCompiler.compileLine(
            "item in [gregtech:gt.metaitem.01:1, gregtech:gt.metaitem.01:2] && mod = gregtech -> 1024",
            16,
        )

        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 2)))
        assertEquals(false, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 3)))
    }

    @Test
    fun `shouldSupportItemAndMetaList`() {
        val compiled = RuleCompiler.compileLine(
            "item = gregtech:gt.metaitem.01 && meta in [1, 2, 3] -> 1024",
            17,
        )

        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 2)))
        assertEquals(false, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 4)))
        assertEquals(false, compiled.matches(ctx("gregtech:meta_ingot", meta = 2)))
    }

    @Test
    fun `and_shouldHaveHigherPrecedenceThanOr`() {
        val compiled = RuleCompiler.compileLine(
            "mod = thermal || item = gregtech:gt.metaitem.01 && metadata = 11305 -> 256",
            18,
        )

        assertEquals(true, compiled.matches(ctx("thermal:foo")))
        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 11305)))
        assertEquals(false, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 1)))
    }

    @Test
    fun `itemWildcard_shouldMatchStackableOnly`() {
        val compiled = RuleCompiler.compileLine("item = * -> 128", 19)

        // baseSize > 1 可堆叠
        assertEquals(true, compiled.matches(ctx("minecraft:egg", baseSize = 16)))
        assertEquals(true, compiled.matches(ctx("minecraft:stick")))

        // baseSize = 1 不可堆叠（工具、装备、桶等）
        assertEquals(false, compiled.matches(ctx("minecraft:diamond_sword", baseSize = 1)))
        assertEquals(false, compiled.matches(ctx("minecraft:water_bucket", baseSize = 1)))
    }

    @Test
    fun `shouldMatchTabField`() {
        val compiled = RuleCompiler.compileLine("tab = buildingBlocks -> 256", 20)

        assertEquals(true, compiled.matches(ctx("minecraft:stone", type = "block", tab = "buildingBlocks")))
        assertEquals(false, compiled.matches(ctx("minecraft:stick", tab = "tools")))
    }

    @Test
    fun `shouldMatchMaterialFieldExactly`() {
        val compiled = RuleCompiler.compileLine("material = steel -> 2048", 21)

        assertEquals(true, compiled.matches(ctx("gregtech:meta_item_1", meta = 1000, material = "steel")))
        assertEquals(false, compiled.matches(ctx("gregtech:meta_item_1", meta = 1001, material = "copper")))
        assertEquals(false, compiled.matches(ctx("gregtech:meta_item_1", meta = 1002)))
    }

    @Test
    fun `shouldMatchMaterialListAndRejectEmptyDefault`() {
        val compiled = RuleCompiler.compileLine("material in [steel, copper] -> 2048", 22)

        assertEquals(true, compiled.matches(ctx("gregtech:meta_item_1", meta = 1000, material = "steel")))
        assertEquals(true, compiled.matches(ctx("gregtech:meta_item_1", meta = 1001, material = "copper")))
        assertEquals(false, compiled.matches(ctx("gregtech:meta_item_1", meta = 1002)))
    }

    @Test
    fun `materialMissing_shouldNotMatchNegativeComparison`() {
        val compiled = RuleCompiler.compileLine("material != steel -> 2048", 23)

        assertEquals(false, compiled.matches(ctx("gregtech:meta_item_1", meta = 1000)))
        assertEquals(false, compiled.matches(ctx("gregtech:meta_item_1", meta = 1001, material = "steel")))
        assertEquals(true, compiled.matches(ctx("gregtech:meta_item_1", meta = 1002, material = "copper")))
    }

    @Test
    fun `itemList_shouldReuseItemMatcherMetadataSugar`() {
        val compiled = RuleCompiler.compileLine("item in [gregtech:gt.metaitem.01@11305] -> 1024", 24)

        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 11305)))
        assertEquals(false, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 42)))
    }

    @Test
    fun `shouldMatchMetaRange`() {
        val compiled = RuleCompiler.compileLine("100 < meta < 300 -> 512", 25)

        assertEquals(true, compiled.matches(ctx("minecraft:wool", meta = 150, type = "block")))
        assertEquals(false, compiled.matches(ctx("minecraft:wool", meta = 50, type = "block")))
        assertEquals(false, compiled.matches(ctx("minecraft:wool", meta = 400, type = "block")))
    }

    private fun ctx(
        itemId: String = "minecraft:egg",
        modId: String = itemId.substringBefore(':'),
        meta: Int = 0,
        baseSize: Int = 64,
        type: String = "item",
        oreNames: Set<String> = emptySet(),
        tab: String = "",
        material: String = "",
    ) = StackContext(
        itemId = itemId,
        modId = modId,
        metadata = meta,
        baseLimit = baseSize,
        type = type,
        oreNames = oreNames,
        tab = tab,
        material = material,
    )
}
