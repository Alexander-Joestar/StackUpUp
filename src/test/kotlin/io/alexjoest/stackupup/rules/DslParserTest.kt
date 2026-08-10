package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.rules.ast.FieldComparisonAst
import io.alexjoest.stackupup.rules.ast.RangeConditionAst
import io.alexjoest.stackupup.rules.parse.DslParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DslParserTest {
    @Test
    fun shouldParseChainedComparison() {
        val rule = DslParser.parseLine("2 < size < 64 -> 1024")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(1024), rule.action.steps.map { it.value })
        assertEquals(listOf(RuleField.SIZE), rule.condition.debugFields())
    }

    @Test
    fun shouldParseInList() {
        val rule = DslParser.parseLine("item in [minecraft:egg, minecraft:snowball] -> 128")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(128), rule.action.steps.map { it.value })
        assertEquals(2, rule.condition.debugLiteralCount())
    }

    @Test
    fun shouldParseAndAfterListCondition() {
        val rule = DslParser.parseLine("item in [minecraft:egg, minecraft:snowball] && metadata = 0 -> 128")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(128), rule.action.steps.map { it.value })
        assertEquals(listOf(RuleField.ITEM, RuleField.META), rule.condition.debugFields())
        assertEquals(3, rule.condition.debugLiteralCount())
    }

    @Test
    fun shouldParseMultiplyOperator() {
        val rule = DslParser.parseLine("size > 2 -> *4")
        assertEquals(listOf("multiply"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(4), rule.action.steps.map { it.value })
        assertEquals(listOf(RuleField.SIZE), rule.condition.debugFields())
    }

    @Test
    fun shouldNormalizeSymbolAliases() {
        val rule = DslParser.parseLine("item = gregtech:gt.metaitem.01 && metadata in [1, 2, 3] -> 1024")
        assertEquals(listOf("set"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(1024), rule.action.steps.map { it.value })
        assertEquals(listOf(RuleField.ITEM, RuleField.META), rule.condition.debugFields())
        assertEquals(4, rule.condition.debugLiteralCount())
    }

    @Test
    fun shouldParseActionChain() {
        val rule = DslParser.parseLine("size > 1 -> *2 -> +10 -> /2")
        assertEquals(listOf("multiply", "add", "divide"), rule.action.steps.map { it.debugName })
        assertEquals(listOf(2, 10, 2), rule.action.steps.map { it.value })
    }

    @Test
    fun unknownFieldSingleComparison_shouldThrowUnsupportedField() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            DslParser.parseLine("bogus = 1 -> 64")
        }

        assertEquals(RuleMessageKey.UNSUPPORTED_FIELD.translationKey, error.messageData.translationKey)
    }

    @Test
    fun compactChainedRange_shouldProduceRangeConditionAst() {
        val range = DslParser.parseLine("1<meta<3 -> 512").condition as RangeConditionAst

        assertEquals(RuleField.META, range.field)
        assertEquals("1", range.lower)
        assertEquals(false, range.lowerInclusive)
        assertEquals("3", range.upper)
        assertEquals(false, range.upperInclusive)
    }

    @Test
    fun closedChainedRange_shouldProduceInclusiveBounds() {
        val range = DslParser.parseLine("1 <= meta <= 3 -> 512").condition as RangeConditionAst

        assertEquals(RuleField.META, range.field)
        assertEquals("1", range.lower)
        assertEquals(true, range.lowerInclusive)
        assertEquals("3", range.upper)
        assertEquals(true, range.upperInclusive)
    }

    @Test
    fun reversedChainedRange_shouldNormalizeToLowerAndUpperBounds() {
        // 3 >= meta > 1 与 1 < meta <= 3 是同一区间，边界方向统一归一到 lower/upper
        val range = DslParser.parseLine("3 >= meta > 1 -> 512").condition as RangeConditionAst

        assertEquals(RuleField.META, range.field)
        assertEquals("1", range.lower)
        assertEquals(false, range.lowerInclusive)
        assertEquals("3", range.upper)
        assertEquals(true, range.upperInclusive)
    }

    @Test
    fun chainLongerThanTwoComparisons_shouldNotSilentlyTruncate() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            DslParser.parseLine("1 < meta < 3 < 5 -> 512")
        }

        assertEquals(RuleMessageKey.TRAILING_CONTENT.translationKey, error.messageData.translationKey)
    }

    @Test
    fun singleComparisonStartingWithLiteral_shouldReverseOperator() {
        // literal cmp field 单比较等价于 field cmp.reverse() literal，产出 FieldComparisonAst
        val comparison = DslParser.parseLine("3 > meta -> 512").condition as FieldComparisonAst

        assertEquals(RuleField.META, comparison.field)
        assertEquals(ComparisonOperator.LESS, comparison.operator)
        assertEquals("3", comparison.literal)
    }
}
