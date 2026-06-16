package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.field.RuleFieldContextProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RuleLanguageTest {
    @Test
    fun `fieldIdentifier_shouldResolveToUnifiedEnum`() {
        assertEquals(RuleField.ITEM, RuleField.fromIdentifier("item"))
        assertEquals(RuleField.MOD, RuleField.fromIdentifier("mod"))
        assertEquals(RuleField.TYPE, RuleField.fromIdentifier("type"))
        assertEquals(RuleField.MATERIAL, RuleField.fromIdentifier("material"))
        assertEquals(RuleField.META, RuleField.fromIdentifier("metadata"))
        assertEquals(RuleField.SIZE, RuleField.fromIdentifier("size"))
        assertNull(RuleField.fromIdentifier("unknown"))
    }

    @Test
    fun `comparisonOperator_shouldSupportReverseChaining`() {
        assertEquals(ComparisonOperator.GREATER, ComparisonOperator.LESS.reverse())
        assertEquals(ComparisonOperator.GREATER_EQUALS, ComparisonOperator.LESS_EQUALS.reverse())
        assertEquals(ComparisonOperator.LESS, ComparisonOperator.GREATER.reverse())
        assertEquals(ComparisonOperator.LESS_EQUALS, ComparisonOperator.GREATER_EQUALS.reverse())
    }

    @Test
    fun `comparisonSymbols_shouldResolveToEnum`() {
        assertEquals(ComparisonOperator.EQUALS, ComparisonOperator.fromSymbol("="))
        assertEquals(ComparisonOperator.NOT_EQUALS, ComparisonOperator.fromSymbol("!="))
        assertEquals(ComparisonOperator.GREATER_EQUALS, ComparisonOperator.fromSymbol(">="))
        assertEquals(ComparisonOperator.LESS_EQUALS, ComparisonOperator.fromSymbol("<="))
    }

    @Test
    fun `fieldMetadata_shouldDeclareContextRequirements`() {
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
    fun `cacheKeyField_shouldExtractDeclaredFieldValue`() {
        assertEquals(
            "steel",
            RuleField.MATERIAL.cacheKeyValue(cacheCtx(material = "steel"))
        )
    }

    private fun cacheCtx(
        itemId: String = "gregtech:meta_item_1",
        modId: String = itemId.substringBefore(':'),
        metadata: Int = 1000,
        type: String = "item",
        baseLimit: Int = 64,
        oreNames: Set<String> = emptySet(),
        tab: String = "",
        material: String = "",
    ) = StackContext(
        itemId = itemId,
        modId = modId,
        metadata = metadata,
        type = type,
        baseLimit = baseLimit,
        oreNames = oreNames,
        tab = tab,
        material = material,
    )
}
