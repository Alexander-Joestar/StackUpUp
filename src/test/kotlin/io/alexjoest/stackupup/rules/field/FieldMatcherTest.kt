package io.alexjoest.stackupup.rules.field

import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.LocalizedRuleException
import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T5：sealed 表达式树 matcher 的行为测试。
 *
 * 覆盖：链式区间与空区间、数值开闭边界、非法字段×运算符组合 fail-fast、
 * 纯字符串列表 HashSet 命中、通配列表逐项匹配、NOT_EQUALS 取反与缺失值策略优先级。
 */
class FieldMatcherTest {
    @Test
    fun `chainedInterval_shouldMatchOnlyMiddleValue`() {
        val compiled = RuleCompiler.compileLine("1 < meta < 3 -> 512", 1)

        assertTrue(compiled.matches(ctx(meta = 2)))
        assertFalse(compiled.matches(ctx(meta = 1)))
        assertFalse(compiled.matches(ctx(meta = 3)))
    }

    @Test
    fun `compactChainedInterval_shouldMatchOnlyMiddleValue`() {
        val compiled = RuleCompiler.compileLine("1<meta<3 -> 512", 1)

        assertTrue(compiled.matches(ctx(meta = 2)))
        assertFalse(compiled.matches(ctx(meta = 1)))
        assertFalse(compiled.matches(ctx(meta = 3)))
    }

    @Test
    fun `reverseDirectionChain_shouldMatchLikeForwardComparison`() {
        // literal cmp field 单比较 ≡ field cmp.reverse() literal：3 > meta 与 meta < 3 语义一致
        val reversed = RuleCompiler.compileLine("3 > meta -> 512", 1)
        val forward = RuleCompiler.compileLine("meta < 3 -> 512", 1)

        for (meta in 0..4) {
            assertEquals(forward.matches(ctx(meta = meta)), reversed.matches(ctx(meta = meta)), "meta=$meta")
        }
    }

    @Test
    fun `reversedRange_shouldMatchLikeForwardRange`() {
        // 反向区间 3 >= meta > 1 ≡ 1 < meta <= 3
        val reversed = RuleCompiler.compileLine("3 >= meta > 1 -> 512", 1)
        val forward = RuleCompiler.compileLine("1 < meta <= 3 -> 512", 1)

        for (meta in 0..4) {
            assertEquals(forward.matches(ctx(meta = meta)), reversed.matches(ctx(meta = meta)), "meta=$meta")
        }
    }

    @Test
    fun `emptyInterval_shouldNeverMatch`() {
        val compiled = RuleCompiler.compileLine("3 < meta < 1 -> 512", 1)

        for (meta in 0..4) {
            assertFalse(compiled.matches(ctx(meta = meta)), "meta=$meta should not match empty interval")
        }
    }

    @Test
    fun `numericBoundary_shouldRespectOpenClosedSemantics`() {
        val open = RuleCompiler.compileLine("1 < meta < 3 -> 512", 1)
        val closed = RuleCompiler.compileLine("1 <= meta <= 3 -> 512", 1)

        assertFalse(open.matches(ctx(meta = 1)))
        assertTrue(open.matches(ctx(meta = 2)))
        assertFalse(open.matches(ctx(meta = 3)))

        assertTrue(closed.matches(ctx(meta = 1)))
        assertTrue(closed.matches(ctx(meta = 3)))
        assertFalse(closed.matches(ctx(meta = 4)))
    }

    @Test
    fun `numericNotEquals_shouldComparePrimitively`() {
        val compiled = RuleCompiler.compileLine("meta != 14 -> 512", 1)

        assertFalse(compiled.matches(ctx(meta = 14)))
        assertTrue(compiled.matches(ctx(meta = 5)))
    }

    @Test
    fun `illegalOrderingOperatorOnStringField_shouldFailFast`() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            RuleCompiler.compileLine("mod > foo -> 64", 1)
        }

        assertEquals(RuleMessageKey.UNSUPPORTED_OPERATOR_FOR_FIELD.translationKey, error.messageData.translationKey)
        assertEquals(listOf("string", ">"), error.messageData.args)
    }

    @Test
    fun `illegalOrderingOperatorOnItemField_shouldFailFast`() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            RuleCompiler.compileLine("item >= minecraft:egg -> 64", 1)
        }

        assertEquals(RuleMessageKey.UNSUPPORTED_OPERATOR_FOR_FIELD.translationKey, error.messageData.translationKey)
        assertEquals(listOf("item", ">="), error.messageData.args)
    }

    @Test
    fun `illegalOrderingOperatorOnStringSetField_shouldFailFast`() {
        val error = assertThrows(LocalizedRuleException::class.java) {
            RuleCompiler.compileLine("ore < ingotIron -> 64", 1)
        }

        assertEquals(RuleMessageKey.UNSUPPORTED_OPERATOR_FOR_FIELD.translationKey, error.messageData.translationKey)
        assertEquals(listOf("string_set", "<"), error.messageData.args)
    }

    @Test
    fun `pureStringList_shouldMatchAnyMember`() {
        val compiled = RuleCompiler.compileLine("mod in [thermal, ic2] -> 512", 1)

        assertTrue(compiled.matches(ctx(modId = "thermal")))
        assertTrue(compiled.matches(ctx(modId = "ic2")))
        assertFalse(compiled.matches(ctx(modId = "vanilla")))
    }

    @Test
    fun `wildcardList_shouldMatchPerItem`() {
        val compiled = RuleCompiler.compileLine("mod in [therm*, ic2] -> 512", 1)

        assertTrue(compiled.matches(ctx(modId = "thermalexpansion")))
        assertTrue(compiled.matches(ctx(modId = "ic2")))
        assertFalse(compiled.matches(ctx(modId = "minecraft")))
    }

    @Test
    fun `stringNotEquals_shouldNegateMatch`() {
        val compiled = RuleCompiler.compileLine("mod != thermal -> 512", 1)

        assertFalse(compiled.matches(ctx(modId = "thermal")))
        assertTrue(compiled.matches(ctx(modId = "ic2")))
    }

    @Test
    fun `stringNotEqualsWildcard_shouldNegatePattern`() {
        val compiled = RuleCompiler.compileLine("mod != therm* -> 512", 1)

        assertFalse(compiled.matches(ctx(modId = "thermalexpansion")))
        assertTrue(compiled.matches(ctx(modId = "ic2")))
    }

    @Test
    fun `itemNotEquals_shouldNegatePatternAndMeta`() {
        val compiled = RuleCompiler.compileLine("item != minecraft:wool@14 -> 512", 1)

        assertFalse(compiled.matches(ctx(itemId = "minecraft:wool", meta = 14)))
        assertTrue(compiled.matches(ctx(itemId = "minecraft:wool", meta = 5)))
        assertTrue(compiled.matches(ctx(itemId = "minecraft:egg", meta = 14)))
    }

    @Test
    fun `itemList_shouldHonorExactMeta`() {
        val compiled = RuleCompiler.compileLine("item in [minecraft:wool@14, minecraft:egg] -> 512", 1)

        assertTrue(compiled.matches(ctx(itemId = "minecraft:wool", meta = 14)))
        assertFalse(compiled.matches(ctx(itemId = "minecraft:wool", meta = 5)))
        assertTrue(compiled.matches(ctx(itemId = "minecraft:egg", meta = 0)))
    }

    @Test
    fun `missingValuePolicy_shouldApplyBeforeNegation`() {
        val compiled = RuleCompiler.compileLine("material != steel -> 512", 1)

        assertFalse(compiled.matches(ctx(material = "")))
        assertFalse(compiled.matches(ctx(material = "steel")))
        assertTrue(compiled.matches(ctx(material = "copper")))
    }

    @Test
    fun `stringListWithNeverMatchPolicy_shouldRejectEmptyValue`() {
        val compiled = RuleCompiler.compileLine("material in [steel, copper] -> 512", 1)

        assertFalse(compiled.matches(ctx(material = "")))
        assertTrue(compiled.matches(ctx(material = "steel")))
        assertTrue(compiled.matches(ctx(material = "copper")))
    }

    @Test
    fun `stringSetWildcard_shouldMatchAnyOreName`() {
        val compiled = RuleCompiler.compileLine("ore = ingot* -> 512", 1)

        assertTrue(compiled.matches(ctx(oreNames = setOf("ingotIron", "dustGold"))))
        assertFalse(compiled.matches(ctx(oreNames = setOf("dustSulfur"))))
    }

    @Test
    fun `stringSetList_shouldMatchAnyMember`() {
        val compiled = RuleCompiler.compileLine("ore in [ingotIron, ingotGold] -> 512", 1)

        assertTrue(compiled.matches(ctx(oreNames = setOf("ingotIron"))))
        assertTrue(compiled.matches(ctx(oreNames = setOf("dustGold", "ingotGold"))))
        assertFalse(compiled.matches(ctx(oreNames = setOf("dustSulfur"))))
    }

    @Test
    fun `itemWildcardList_shouldMatchStackableOnly`() {
        val compiled = RuleCompiler.compileLine("item in [*] -> 512", 1)

        assertTrue(compiled.matches(ctx(baseLimit = 16)))
        assertFalse(compiled.matches(ctx(baseLimit = 1)))
    }

    @Test
    fun `numericList_shouldMatchAnyMember`() {
        val compiled = RuleCompiler.compileLine("meta in [1, 2, 3] -> 512", 1)

        assertTrue(compiled.matches(ctx(meta = 2)))
        assertFalse(compiled.matches(ctx(meta = 4)))
    }

    @Test
    fun `stringFieldEquality_shouldMatchOtherStringFields`() {
        val typeRule = RuleCompiler.compileLine("type = block -> 512", 1)
        val tabRule = RuleCompiler.compileLine("tab = buildingBlocks -> 512", 1)

        assertTrue(typeRule.matches(ctx(type = "block")))
        assertFalse(typeRule.matches(ctx(type = "item")))
        assertTrue(tabRule.matches(ctx(tab = "buildingBlocks")))
        assertFalse(tabRule.matches(ctx(tab = "tools")))
    }

    private fun ctx(
        itemId: String = "minecraft:egg",
        modId: String = "minecraft",
        meta: Int = 0,
        type: String = "item",
        baseLimit: Int = 64,
        oreNames: Set<String> = emptySet(),
        tab: String = "",
        material: String = "",
    ) = StackContext(
        itemId = itemId,
        modId = modId,
        metadata = meta,
        type = type,
        baseLimit = baseLimit,
        oreNames = oreNames,
        tab = tab,
        material = material,
    )
}
