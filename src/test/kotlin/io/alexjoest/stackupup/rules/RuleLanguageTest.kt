package io.alexjoest.stackupup.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RuleLanguageTest {
    @Test
    fun `fieldIdentifier_shouldResolveToUnifiedEnum`() {
        assertEquals(RuleField.ITEM, RuleField.fromIdentifier("item"))
        assertEquals(RuleField.MOD, RuleField.fromIdentifier("mod"))
        assertEquals(RuleField.TYPE, RuleField.fromIdentifier("type"))
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
}
