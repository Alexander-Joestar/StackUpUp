package io.alexjoest.stackupup.rules.field

import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.ComparisonOperator
import io.alexjoest.stackupup.rules.FieldType
import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.RuleMessages

/**
 * 字段条件 matcher 工厂：把字段比较表达式编译成 [FieldMatcher] 表达式树节点。
 *
 * 字段只负责把比较表达式编译成命中判断，堆叠数量仍由规则 action 决定。
 */
internal fun interface RuleFieldMatcherFactory {
    fun compile(operator: ComparisonOperator, literal: String): FieldMatcher

    /**
     * 列表条件：复用字段自身的等值语义 OR 组合。
     * 单值字符串字段覆盖此默认实现，纯字面量列表用 HashSet 命中。
     */
    fun compileList(literals: List<String>): FieldMatcher = AnyOfFieldMatcher(literals.map { compile(ComparisonOperator.EQUALS, it) })
}

/**
 * 字段值缺失时的比较策略。
 */
internal enum class MissingValuePolicy {
    EMPTY_VALUE,
    NEVER_MATCH,
}

/**
 * 字段类型×运算符合法性表：排序运算符只对数值字段合法。
 *
 * 非法组合在编译期 fail-fast 抛错，不再像旧实现那样静默返回 false。
 */
private fun ComparisonOperator.requireEqualityFor(fieldType: FieldType): ComparisonOperator = when (this) {
    ComparisonOperator.EQUALS, ComparisonOperator.NOT_EQUALS -> this
    else -> throw RuleMessages.exception(
        RuleMessageKey.UNSUPPORTED_OPERATOR_FOR_FIELD,
        fieldType.name.lowercase(),
        symbol,
    )
}

/**
 * 内置字段 matcher 集合。
 *
 * 保持不同字段的特殊语义集中在这里，避免条件编译器按字段名重复分发。
 */
internal object RuleFieldMatchers {
    fun item(): RuleFieldMatcherFactory = RuleFieldMatcherFactory { operator, literal ->
        operator.requireEqualityFor(FieldType.ITEM)
        if (literal == "*") {
            // "item = *" 匹配所有可堆叠物品（原版 baseSize > 1）
            ItemStackableMatcher(negate = operator == ComparisonOperator.NOT_EQUALS)
        } else {
            val pattern = RuleLiteralMatcherCompiler.parseItemPattern(literal)
            ItemPatternMatcher(
                itemIdMatcher = pattern.itemIdMatcher,
                meta = pattern.meta,
                negate = operator == ComparisonOperator.NOT_EQUALS,
            )
        }
    }

    fun string(selector: (StackContext) -> String, missingValuePolicy: MissingValuePolicy = MissingValuePolicy.EMPTY_VALUE): RuleFieldMatcherFactory =
        StringRuleFieldMatcherFactory(selector, missingValuePolicy)

    fun stringSet(selector: (StackContext) -> Iterable<String>): RuleFieldMatcherFactory = RuleFieldMatcherFactory { operator, literal ->
        operator.requireEqualityFor(FieldType.STRING_SET)
        StringSetAnyMatcher(
            selector = selector,
            valueMatcher = RuleLiteralMatcherCompiler.compileStringMatcher(literal),
            negate = operator == ComparisonOperator.NOT_EQUALS,
        )
    }

    fun numeric(selector: (StackContext) -> Int): RuleFieldMatcherFactory = RuleFieldMatcherFactory { operator, literal ->
        ComparisonFieldMatcher(operator, literal.toInt(), selector)
    }
}

/**
 * 单值字符串字段工厂：`compileList` 覆盖为纯字面量 HashSet 命中，
 * 含通配成员时回落逐项匹配。
 */
private class StringRuleFieldMatcherFactory(private val selector: (StackContext) -> String, private val missingValuePolicy: MissingValuePolicy) :
    RuleFieldMatcherFactory {
    override fun compile(operator: ComparisonOperator, literal: String): FieldMatcher {
        operator.requireEqualityFor(FieldType.STRING)
        return StringFieldMatcher(
            selector = selector,
            valueMatcher = RuleLiteralMatcherCompiler.compileStringMatcher(literal),
            negate = operator == ComparisonOperator.NOT_EQUALS,
            missingValuePolicy = missingValuePolicy,
        )
    }

    override fun compileList(literals: List<String>): FieldMatcher {
        if (literals.none { '*' in it }) {
            return StringMembershipMatcher(selector, literals.toHashSet())
        }
        return AnyOfFieldMatcher(literals.map { compile(ComparisonOperator.EQUALS, it) })
    }
}
