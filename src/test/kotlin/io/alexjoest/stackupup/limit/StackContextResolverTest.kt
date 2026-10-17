package io.alexjoest.stackupup.limit

import net.minecraft.creativetab.CreativeTabs
import net.minecraft.init.Blocks
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class StackContextResolverTest {
    @Test
    fun shouldNormalizeItemStackToUnifiedContext() {
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("gregtech", "meta_ingot"))
        val stack = ItemStack(item, 1, 324)
        var oreDictCalls = 0
        val previousIndex = RuleRuntime.oreDictIndex()

        try {
            RuleRuntime.replaceOreDictIndex(
                OreDictIndex.fromStackLoader {
                    oreDictCalls++
                    setOf("ingotSteel")
                },
            )

            val context = StackContextResolver.fromStack(stack = stack, baseLimit = 64) ?: error("context missing")

            assertEquals("gregtech:meta_ingot", context.itemId)
            assertEquals("gregtech", context.modId)
            assertEquals(324, context.metadata)
            assertEquals(64, context.baseLimit)
            assertEquals(0, oreDictCalls)
            assertEquals("item", context.type)
            assertEquals(0, oreDictCalls)
            assertEquals(setOf("ingotSteel"), context.oreNames)
            assertEquals(1, oreDictCalls)
            assertEquals(setOf("ingotSteel"), context.oreNames)
            assertEquals(1, oreDictCalls)
        } finally {
            RuleRuntime.replaceOreDictIndex(previousIndex)
        }
    }

    @Test
    fun shouldResolveBlockTypeFromStack() {
        Bootstrap.register()
        val context = StackContextResolver.fromStack(ItemStack(Blocks.STONE, 1, 0), 64) ?: error("context missing")

        assertEquals("block", context.type)
    }

    @Test
    fun shouldUseDirectOptionalValuesWithoutStack() {
        val context = StackContext(
            itemId = "example:item",
            modId = "example",
            metadata = 3,
            type = "direct type",
            baseLimit = 16,
            oreNames = setOf("directOre"),
            tab = "direct tab",
            material = "direct material",
        )

        assertEquals("direct type", context.type)
        assertEquals(setOf("directOre"), context.oreNames)
        assertEquals("direct tab", context.tab)
        assertEquals("direct material", context.material)
    }

    @Test
    fun shouldResolveOptionalFieldsLazilyOnce() {
        Bootstrap.register()
        var materialCalls = 0
        var tabCalls = 0
        var oreDictCalls = 0
        val restoreResolver = GregTechMaterialResolver.installResolverForTesting {
            materialCalls++
            "steel"
        }
        val item = object : Item() {
            override fun getCreativeTab(): CreativeTabs? {
                tabCalls++
                return CreativeTabs.BUILDING_BLOCKS
            }
        }.setRegistryName(ResourceLocation("gregtech", "meta_ingot"))
        val stack = ItemStack(item, 1, 324)
        val previousIndex = RuleRuntime.oreDictIndex()

        try {
            RuleRuntime.replaceOreDictIndex(
                OreDictIndex.fromStackLoader {
                    oreDictCalls++
                    setOf("ingotSteel")
                },
            )

            val context = StackContextResolver.fromStack(stack = stack, baseLimit = 64) ?: error("context missing")
            assertEquals(0, oreDictCalls)
            assertEquals(0, materialCalls)
            assertEquals(0, tabCalls)

            assertEquals(setOf("ingotSteel"), context.oreNames)
            assertEquals(1, oreDictCalls)
            assertEquals(setOf("ingotSteel"), context.oreNames)
            assertEquals(1, oreDictCalls)

            assertEquals("buildingBlocks", context.tab)
            assertEquals(1, tabCalls)
            assertEquals("buildingBlocks", context.tab)
            assertEquals(1, tabCalls)

            assertEquals("steel", context.material)
            assertEquals(1, materialCalls)
            assertEquals("steel", context.material)
            assertEquals(1, materialCalls)
        } finally {
            RuleRuntime.replaceOreDictIndex(previousIndex)
            restoreResolver()
        }
    }

    @Test
    fun identityMethodsShouldNotResolveOptionalFields() {
        Bootstrap.register()
        var materialCalls = 0
        var tabCalls = 0
        var oreDictCalls = 0
        val restoreResolver = GregTechMaterialResolver.installResolverForTesting {
            materialCalls++
            "steel"
        }
        val item = object : Item() {
            override fun getCreativeTab(): CreativeTabs? {
                tabCalls++
                return CreativeTabs.BUILDING_BLOCKS
            }
        }.setRegistryName(ResourceLocation("gregtech", "meta_ingot"))
        val stack = ItemStack(item, 1, 324)
        val previousIndex = RuleRuntime.oreDictIndex()

        try {
            RuleRuntime.replaceOreDictIndex(
                OreDictIndex.fromStackLoader {
                    oreDictCalls++
                    setOf("ingotSteel")
                },
            )

            val context = StackContextResolver.fromStack(stack = stack, baseLimit = 64) ?: error("context missing")
            val other = StackContextResolver.fromStack(stack = stack, baseLimit = 64) ?: error("context missing")

            context.hashCode()
            context.toString()
            assertFalse(context == other)
            assertEquals(0, oreDictCalls)
            assertEquals(0, materialCalls)
            assertEquals(0, tabCalls)
        } finally {
            RuleRuntime.replaceOreDictIndex(previousIndex)
            restoreResolver()
        }
    }
}
