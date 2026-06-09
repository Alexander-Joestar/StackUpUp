package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.rules.field.RuleFieldCacheContext
import io.alexjoest.stackupup.rules.model.RuleMatchContext
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
        assertEquals(emptySet<RuleContextRequirement>(), RuleField.ITEM.requirements)
        assertEquals(false, RuleField.ITEM.contributesToCacheKey())

        assertEquals(setOf(RuleContextRequirement.ORE_NAMES), RuleField.ORE.requirements)
        assertEquals(false, RuleField.ORE.contributesToCacheKey())

        assertEquals(setOf(RuleContextRequirement.MATERIAL), RuleField.MATERIAL.requirements)
        assertEquals(true, RuleField.MATERIAL.contributesToCacheKey())

        assertEquals(emptySet<RuleContextRequirement>(), RuleField.TAB.requirements)
        assertEquals(true, RuleField.TAB.contributesToCacheKey())
    }

    @Test
    fun `cacheKeyField_shouldExtractDeclaredFieldValue`() {
        val context = RuleMatchContext(
            itemId = "gregtech:meta_item_1",
            modId = "gregtech",
            meta = 1000,
            baseSize = 64,
            type = "item",
            oreNames = emptySet(),
            material = "steel",
        )

        assertEquals(
            "steel",
            RuleField.MATERIAL.cacheKeyValue(
                RuleFieldCacheContext(
                    itemId = context.itemId,
                    modId = context.modId,
                    metadata = context.meta,
                    type = context.type,
                    baseLimit = context.baseSize,
                    tab = context.tab,
                    material = context.material,
                )
            )
        )
    }
}
