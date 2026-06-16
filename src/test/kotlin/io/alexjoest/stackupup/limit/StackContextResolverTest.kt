package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.rules.RuleContextRequirement
import io.alexjoest.stackupup.rules.RuleField
import io.alexjoest.stackupup.rules.compile.RuntimeContextRequirements
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StackContextResolverTest {
    @Test
    fun `shouldNormalizeItemStackToUnifiedContext`() {
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("gregtech", "meta_ingot"))
        val stack = ItemStack(item, 1, 324)
        val index = OreDictIndex.fromStackLoader { setOf("ingotSteel") }
        val previousIndex = RuleRuntime.oreDictIndex()
        try {
            RuleRuntime.replaceOreDictIndex(index)

            val context = StackContextResolver.fromStack(stack = stack, baseLimit = 64)

            assertNotNull(context)
            assertEquals("gregtech:meta_ingot", context?.itemId)
            assertEquals("gregtech", context?.modId)
            assertEquals(324, context?.metadata)
            assertEquals("item", context?.type)
            assertEquals(64, context?.baseLimit)
            assertEquals(setOf("ingotSteel"), context?.oreNames)
        } finally {
            RuleRuntime.replaceOreDictIndex(previousIndex)
        }
    }

    @Test
    fun `shouldSkipOreDictLookupWhenDisabled`() {
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("gregtech", "meta_ingot"))
        val stack = ItemStack(item, 1, 324)
        val previousIndex = RuleRuntime.oreDictIndex()

        try {
            RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { error("ore dict must not be queried") })

            val context = StackContextResolver.fromStack(
                stack = stack,
                baseLimit = 64,
                requirements = RuntimeContextRequirements.EMPTY,
            )

            assertNotNull(context)
            assertTrue(context?.oreNames?.isEmpty() == true)
        } finally {
            RuleRuntime.replaceOreDictIndex(previousIndex)
        }
    }

    @Test
    fun `shouldReturnEmptyMaterialWhenMaterialLookupDisabled`() {
        Bootstrap.register()
        var calls = 0
        val restoreResolver = GregTechMaterialResolver.installResolverForTesting {
            calls++
            "steel"
        }
        val item = Item().setRegistryName(ResourceLocation("gregtech", "meta_ingot"))
        val stack = ItemStack(item, 1, 324)

        try {
            val context = StackContextResolver.fromStack(
                stack = stack,
                baseLimit = 64,
                requirements = RuntimeContextRequirements.EMPTY,
            )

            assertNotNull(context)
            assertEquals("", context?.material)
            assertEquals(0, calls)
        } finally {
            restoreResolver()
        }
    }

    @Test
    fun `shouldResolveOnlyRequiredExpensiveFields`() {
        Bootstrap.register()
        var materialCalls = 0
        val restoreResolver = GregTechMaterialResolver.installResolverForTesting {
            materialCalls++
            "steel"
        }
        val item = Item().setRegistryName(ResourceLocation("gregtech", "meta_ingot"))
        val stack = ItemStack(item, 1, 324)
        val index = OreDictIndex.fromStackLoader { setOf("ingotSteel") }
        val previousIndex = RuleRuntime.oreDictIndex()
        try {
            RuleRuntime.replaceOreDictIndex(index)

            val context = StackContextResolver.fromStack(
                stack = stack,
                baseLimit = 64,
                requirements = RuntimeContextRequirements.of(
                    RuleContextRequirement.ORE_NAMES,
                    RuleContextRequirement.MATERIAL,
                ),
            )

            assertNotNull(context)
            assertEquals(setOf("ingotSteel"), context?.oreNames)
            assertEquals("steel", context?.material)
            assertEquals(1, materialCalls)
        } finally {
            RuleRuntime.replaceOreDictIndex(previousIndex)
            restoreResolver()
        }
    }

    @Test
    fun `shouldCollectTabFromFieldPlanWithoutOtherOptionalLookups`() {
        Bootstrap.register()
        var materialCalls = 0
        val restoreResolver = GregTechMaterialResolver.installResolverForTesting {
            materialCalls++
            "steel"
        }
        val item = Item()
            .setRegistryName(ResourceLocation("minecraft", "stone"))
            .setCreativeTab(CreativeTabs.BUILDING_BLOCKS)
        val stack = ItemStack(item, 1, 0)
        val previousIndex = RuleRuntime.oreDictIndex()

        try {
            RuleRuntime.replaceOreDictIndex(OreDictIndex.fromStackLoader { error("ore dict must not be queried") })

            val context = StackContextResolver.fromStack(
                stack = stack,
                baseLimit = 64,
                requirements = RuntimeContextRequirements.fromFields(setOf(RuleField.TAB)),
            )

            assertNotNull(context)
            assertEquals("buildingBlocks", context?.tab)
            assertTrue(context?.oreNames?.isEmpty() == true)
            assertEquals("", context?.material)
            assertEquals(0, materialCalls)
        } finally {
            RuleRuntime.replaceOreDictIndex(previousIndex)
            restoreResolver()
        }
    }
}
