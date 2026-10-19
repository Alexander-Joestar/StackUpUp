package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.limit.GregTechMaterialResolver
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.field.RuleFieldContextProvider
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleLanguageTest {
    @Test
    fun fieldIdentifier_shouldResolveToUnifiedEnum() {
        assertEquals(RuleField.ITEM, RuleField.fromIdentifier("item"))
        assertEquals(RuleField.MOD, RuleField.fromIdentifier("mod"))
        assertEquals(RuleField.TYPE, RuleField.fromIdentifier("type"))
        assertEquals(RuleField.MATERIAL, RuleField.fromIdentifier("material"))
        assertEquals(RuleField.META, RuleField.fromIdentifier("metadata"))
        assertEquals(RuleField.SIZE, RuleField.fromIdentifier("size"))
        assertNull(RuleField.fromIdentifier("unknown"))
    }

    @Test
    fun comparisonOperator_shouldSupportReverseChaining() {
        assertEquals(ComparisonOperator.GREATER, ComparisonOperator.LESS.reverse())
        assertEquals(ComparisonOperator.GREATER_EQUALS, ComparisonOperator.LESS_EQUALS.reverse())
        assertEquals(ComparisonOperator.LESS, ComparisonOperator.GREATER.reverse())
        assertEquals(ComparisonOperator.LESS_EQUALS, ComparisonOperator.GREATER_EQUALS.reverse())
    }

    @Test
    fun comparisonSymbols_shouldResolveToEnum() {
        assertEquals(ComparisonOperator.EQUALS, ComparisonOperator.fromSymbol("="))
        assertEquals(ComparisonOperator.NOT_EQUALS, ComparisonOperator.fromSymbol("!="))
        assertEquals(ComparisonOperator.GREATER_EQUALS, ComparisonOperator.fromSymbol(">="))
        assertEquals(ComparisonOperator.LESS_EQUALS, ComparisonOperator.fromSymbol("<="))
    }

    @Test
    fun fieldMetadata_shouldDeclareContextRequirements() {
        assertEquals(FieldType.ITEM, RuleField.ITEM.fieldType)
        assertEquals(emptySet<RuleFieldContextProvider>(), RuleField.ITEM.contextProviders)
        assertEquals(false, RuleField.ITEM.contributesToCacheKey())

        assertEquals(setOf(RuleFieldContextProvider.ORE_NAMES), RuleField.ORE.contextProviders)
        assertEquals(false, RuleField.ORE.contributesToCacheKey())

        assertEquals(setOf(RuleFieldContextProvider.MATERIAL), RuleField.MATERIAL.contextProviders)
        assertEquals(true, RuleField.MATERIAL.contributesToCacheKey())

        assertEquals(setOf(RuleFieldContextProvider.TAB), RuleField.TAB.contextProviders)
        assertEquals(true, RuleField.TAB.contributesToCacheKey())
    }

    @Test
    fun cacheKeyField_shouldExtractDeclaredFieldValue() {
        Bootstrap.register()
        val restoreResolver = GregTechMaterialResolver.installResolverForTesting { stack ->
            if (stack.metadata == MATERIAL_META) "steel" else ""
        }

        try {
            assertEquals(
                "steel",
                RuleField.MATERIAL.cacheKeyValue(cacheCtx()),
            )
        } finally {
            restoreResolver()
        }
    }

    @Test
    fun fieldCacheKeyStrategies_shouldBeExplicitlyDeclared() {
        // T6：每个字段显式声明缓存键贡献策略（必填构造参数），ORE 由身份稳定性契约覆盖而非注释。
        assertEquals(CacheKeyStrategy.IDENTITY_FIXED, RuleField.ITEM.cacheKeyStrategy)
        assertEquals(CacheKeyStrategy.IDENTITY_FIXED, RuleField.MOD.cacheKeyStrategy)
        assertEquals(CacheKeyStrategy.IDENTITY_FIXED, RuleField.TYPE.cacheKeyStrategy)
        assertEquals(CacheKeyStrategy.IDENTITY_FIXED, RuleField.META.cacheKeyStrategy)
        assertEquals(CacheKeyStrategy.IDENTITY_FIXED, RuleField.SIZE.cacheKeyStrategy)
        assertEquals(CacheKeyStrategy.VALUE_CONTRIBUTED, RuleField.MATERIAL.cacheKeyStrategy)
        assertEquals(CacheKeyStrategy.VALUE_CONTRIBUTED, RuleField.TAB.cacheKeyStrategy)
        assertEquals(CacheKeyStrategy.STABLE_VIA_IDENTITY, RuleField.ORE.cacheKeyStrategy)

        for (field in RuleField.entries) {
            assertTrue(field.isCacheKeyCovered(), "${field.name} 必须被缓存键覆盖")
        }
    }

    private fun cacheCtx(): StackContext {
        val item = Item().setRegistryName(ResourceLocation("gregtech", "meta_item_1"))
        return StackContext(
            itemId = "gregtech:meta_item_1",
            metadata = MATERIAL_META,
            baseLimit = 64,
            stack = ItemStack(item, 1, MATERIAL_META),
        )
    }

    private companion object {
        private const val MATERIAL_META: Int = 1000
    }
}
