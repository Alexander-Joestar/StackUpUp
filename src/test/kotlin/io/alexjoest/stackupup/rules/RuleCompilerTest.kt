package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.limit.GregTechMaterialResolver
import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.init.Blocks
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RuleCompilerTest {
    private lateinit var previousOreDictIndex: OreDictIndex
    private lateinit var restoreMaterialResolver: () -> Unit
    private val oreNamesByItemPath: MutableMap<String, Set<String>> = mutableMapOf()
    private val gregMetaItem: Item by lazy { Item().setRegistryName(ResourceLocation("gregtech", "meta_item_1")) }

    @BeforeEach
    fun installContextSources() {
        Bootstrap.register()
        previousOreDictIndex = RuleRuntime.oreDictIndex()
        RuleRuntime.replaceOreDictIndex(
            OreDictIndex.fromStackLoader { stack -> oreNamesByItemPath[stack.item.registryName?.path].orEmpty() },
        )
        restoreMaterialResolver = GregTechMaterialResolver.installResolverForTesting { stack ->
            if (stack.item === gregMetaItem) MATERIAL_BY_META[stack.metadata].orEmpty() else ""
        }
    }

    @AfterEach
    fun restoreContextSources() {
        RuleRuntime.replaceOreDictIndex(previousOreDictIndex)
        restoreMaterialResolver()
    }

    @Test
    fun shouldCompileItemInListToMatchAny() {
        val compiled = RuleCompiler.compileLine("item in [minecraft:egg, minecraft:snowball] -> 128", 7)
        val egg = ctx("minecraft:egg", baseSize = 16)
        val snowball = ctx("minecraft:snowball", baseSize = 16)
        assertEquals(true, compiled.matches(egg))
        assertEquals(true, compiled.matches(snowball))
    }

    @Test
    fun shouldCompileOrCondition() {
        val compiled = RuleCompiler.compileLine("mod = thermal || mod = ic2 -> 512", 8)
        val thermal = ctx("thermal:foo", baseSize = 16)
        val ic2 = ctx("ic2:bar", baseSize = 16)
        val vanilla = ctx("minecraft:egg", baseSize = 16)
        assertEquals(true, compiled.matches(thermal))
        assertEquals(true, compiled.matches(ic2))
        assertEquals(false, compiled.matches(vanilla))
    }

    @Test
    fun shouldSupportModListWildcard() {
        val compiled = RuleCompiler.compileLine("mod in [therm*, ic2] -> 512", 8)
        val thermal = ctx("thermalexpansion:foo", baseSize = 16)
        val ic2 = ctx("ic2:bar", baseSize = 16)
        val vanilla = ctx("minecraft:egg", baseSize = 16)

        assertEquals(true, compiled.matches(thermal))
        assertEquals(true, compiled.matches(ic2))
        assertEquals(false, compiled.matches(vanilla))
    }

    @Test
    fun shouldPreserveMultiplyOrder() {
        val compiled = RuleCompiler.compileLine("ore = ingotSteel -> *2", 9)
        assertEquals(1, compiled.action.steps.size)
        assertEquals("multiply", compiled.action.steps.single().debugName)
        assertEquals(2, compiled.action.steps.single().value)
    }

    @Test
    fun shouldSupportActionChain() {
        val compiled = RuleCompiler.compileLine("ore = ingotSteel -> *2 -> +10", 10)
        assertEquals(listOf("multiply", "add"), compiled.action.steps.map { it.debugName })
        assertEquals(listOf(2, 10), compiled.action.steps.map { it.value })
    }

    @Test
    fun shouldSupportSizeRange() {
        val compiled = RuleCompiler.compileLine("size > 2 && size < 64 -> 1024", 11)
        assertEquals(true, compiled.matches(ctx("minecraft:egg", baseSize = 16)))
        assertEquals(false, compiled.matches(ctx("minecraft:stick")))
    }

    @Test
    fun shouldSupportCompactSizeComparison() {
        val compiled = RuleCompiler.compileLine("size >2 -> 1000000", 11)
        assertEquals(true, compiled.matches(ctx("minecraft:egg")))
        assertEquals(false, compiled.matches(ctx("minecraft:sword", baseSize = 1)))
    }

    @Test
    fun shouldSupportItemWithMetadataSugar() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01@11305 -> 1024", 12)
        val matched = ctx("gregtech:gt.metaitem.01", meta = 11305)
        val otherMeta = ctx("gregtech:gt.metaitem.01", meta = 42)

        assertEquals(true, compiled.matches(matched))
        assertEquals(false, compiled.matches(otherMeta))
    }

    @Test
    fun shouldSupportOreWildcardMatch() {
        val compiled = RuleCompiler.compileLine("ore != ingot* -> 64", 12)
        val ingot = oreCtx("minecraft:iron_ingot", setOf("ingotIron"))
        val dust = oreCtx("minecraft:gunpowder", setOf("dustSulfur"))

        assertEquals(false, compiled.matches(ingot))
        assertEquals(true, compiled.matches(dust))
    }

    @Test
    fun shouldSupportItemAtMetadataSugar() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01@11305 -> 1024", 13)
        val matched = ctx("gregtech:gt.metaitem.01", meta = 11305)
        val otherMeta = ctx("gregtech:gt.metaitem.01", meta = 42)

        assertEquals(true, compiled.matches(matched))
        assertEquals(false, compiled.matches(otherMeta))
    }

    @Test
    fun shouldSupportMetaAsAlias() {
        val compiled = RuleCompiler.compileLine("metadata in [1, 2, 3] -> 512", 14)
        assertEquals(true, compiled.matches(ctx("minecraft:egg", meta = 2, baseSize = 16)))
        assertEquals(false, compiled.matches(ctx("minecraft:egg", meta = 4, baseSize = 16)))
    }

    @Test
    fun itemWithoutMeta_shouldMatchWholeDomain() {
        val compiled = RuleCompiler.compileLine("item = gregtech:gt.metaitem.01 -> 1024", 15)

        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 1)))
        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 11305)))
        assertEquals(false, compiled.matches(ctx("gregtech:meta_ingot", meta = 324)))
    }

    @Test
    fun shouldSupportItemListWithMeta() {
        val compiled = RuleCompiler.compileLine(
            "item in [gregtech:gt.metaitem.01@1, gregtech:gt.metaitem.01@2] && mod = gregtech -> 1024",
            16,
        )

        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 2)))
        assertEquals(false, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 3)))
    }

    @Test
    fun shouldSupportItemAndMetaList() {
        val compiled = RuleCompiler.compileLine(
            "item = gregtech:gt.metaitem.01 && meta in [1, 2, 3] -> 1024",
            17,
        )

        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 2)))
        assertEquals(false, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 4)))
        assertEquals(false, compiled.matches(ctx("gregtech:meta_ingot", meta = 2)))
    }

    @Test
    fun and_shouldHaveHigherPrecedenceThanOr() {
        val compiled = RuleCompiler.compileLine(
            "mod = thermal || item = gregtech:gt.metaitem.01 && metadata = 11305 -> 256",
            18,
        )

        assertEquals(true, compiled.matches(ctx("thermal:foo")))
        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 11305)))
        assertEquals(false, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 1)))
    }

    @Test
    fun itemWildcard_shouldMatchStackableOnly() {
        val compiled = RuleCompiler.compileLine("item = * -> 128", 19)

        // baseSize > 1 可堆叠
        assertEquals(true, compiled.matches(ctx("minecraft:egg", baseSize = 16)))
        assertEquals(true, compiled.matches(ctx("minecraft:stick")))

        // baseSize = 1 不可堆叠（工具、装备、桶等）
        assertEquals(false, compiled.matches(ctx("minecraft:diamond_sword", baseSize = 1)))
        assertEquals(false, compiled.matches(ctx("minecraft:water_bucket", baseSize = 1)))
    }

    @Test
    fun shouldMatchTabField() {
        val compiled = RuleCompiler.compileLine("tab = buildingBlocks -> 256", 20)

        assertEquals(true, compiled.matches(ctx(itemId = "minecraft:stone", stack = ItemStack(Blocks.STONE))))
        assertEquals(false, compiled.matches(ctx(itemId = "minecraft:stick", stack = ItemStack(tabbedItem(CreativeTabs.TOOLS)))))
    }

    @Test
    fun shouldMatchMaterialFieldExactly() {
        val compiled = RuleCompiler.compileLine("material = steel -> 2048", 21)

        assertEquals(true, compiled.matches(materialCtx("gregtech:meta_item_1", 1000, "steel")))
        assertEquals(false, compiled.matches(materialCtx("gregtech:meta_item_1", 1001, "copper")))
        assertEquals(false, compiled.matches(materialCtx("gregtech:meta_item_1", 1002, "")))
    }

    @Test
    fun shouldMatchMaterialListAndRejectEmptyDefault() {
        val compiled = RuleCompiler.compileLine("material in [steel, copper] -> 2048", 22)

        assertEquals(true, compiled.matches(materialCtx("gregtech:meta_item_1", 1000, "steel")))
        assertEquals(true, compiled.matches(materialCtx("gregtech:meta_item_1", 1001, "copper")))
        assertEquals(false, compiled.matches(materialCtx("gregtech:meta_item_1", 1002, "")))
    }

    @Test
    fun materialMissing_shouldNotMatchNegativeComparison() {
        val compiled = RuleCompiler.compileLine("material != steel -> 2048", 23)

        assertEquals(false, compiled.matches(materialCtx("gregtech:meta_item_1", 1000, "")))
        assertEquals(false, compiled.matches(materialCtx("gregtech:meta_item_1", 1001, "steel")))
        assertEquals(true, compiled.matches(materialCtx("gregtech:meta_item_1", 1002, "copper")))
    }

    @Test
    fun itemList_shouldReuseItemMatcherMetadataSugar() {
        val compiled = RuleCompiler.compileLine("item in [gregtech:gt.metaitem.01@11305] -> 1024", 24)

        assertEquals(true, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 11305)))
        assertEquals(false, compiled.matches(ctx("gregtech:gt.metaitem.01", meta = 42)))
    }

    @Test
    fun shouldMatchMetaRange() {
        val compiled = RuleCompiler.compileLine("100 < meta < 300 -> 512", 25)

        assertEquals(true, compiled.matches(woolCtx(meta = 150)))
        assertEquals(false, compiled.matches(woolCtx(meta = 50)))
        assertEquals(false, compiled.matches(woolCtx(meta = 400)))
    }

    private fun ctx(itemId: String = "minecraft:egg", meta: Int = 0, baseSize: Int = 64, stack: ItemStack? = null) = StackContext(
        itemId = itemId,
        metadata = meta,
        baseLimit = baseSize,
        stack = stack,
    )

    private fun woolCtx(meta: Int) = ctx(itemId = "minecraft:wool", meta = meta, stack = ItemStack(Blocks.WOOL, 1, meta))

    private fun oreCtx(itemId: String, oreNames: Set<String>, meta: Int = 0): StackContext {
        val path = "ore_item_${oreNamesByItemPath.size}"
        oreNamesByItemPath[path] = oreNames
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", path))
        return ctx(itemId = itemId, meta = meta, stack = ItemStack(item, 1, 0))
    }

    private fun materialCtx(itemId: String, meta: Int, material: String): StackContext =
        ctx(itemId = itemId, meta = meta, stack = ItemStack(gregMetaItem, 1, MATERIAL_META.getValue(material)))

    private fun tabbedItem(tab: CreativeTabs): Item = object : Item() {
        override fun getCreativeTab(): CreativeTabs? = tab
    }.setRegistryName(ResourceLocation("stackupup_test", "tabbed_item"))

    private companion object {
        private val MATERIAL_META: Map<String, Int> = mapOf("steel" to 0, "copper" to 1, "" to 2)
        private val MATERIAL_BY_META: Map<Int, String> = mapOf(0 to "steel", 1 to "copper", 2 to "")
    }
}
